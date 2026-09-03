// Package xray is the RedFox Xray mobile shim — a tiny gomobile-friendly
// wrapper around Xray-core.
//
// Built into an AAR with:
//   gomobile bind -javapkg=com.redfox -target=android/arm64,android/arm .
// which produces class  com.redfox.xray.XrayCore  (Kotlin: XrayBridge.kt).
//
// Exposed methods (gomobile lower-cases the exported names for Java):
//   StartConfig(jsonConfig, assetsDir String) boolean
//   AwaitExit()
//   Stop()
package xray

import (
	"os"
	"sync"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf"
)

// XrayCore is the singleton gomobile exports. Kotlin sees an INSTANCE field.
type XrayCore struct {
	mu       sync.Mutex
	instance *core.Instance
	stopCh   chan struct{}
}

// Xray is the package-level singleton gomobile turns into an INSTANCE.
var Xray = &XrayCore{}

// StartConfig boots an Xray instance from a full JSON configuration.
// assetsDir holds geoip.dat/geosite.dat (may be empty).
func (x *XrayCore) StartConfig(jsonConfig string, assetsDir string) bool {
	x.mu.Lock()
	defer x.mu.Unlock()

	if x.instance != nil {
		_ = x.instance.Close()
		x.instance = nil
	}

	if assetsDir != "" {
		_ = os.Setenv("XRAY_LOCATION_ASSET", assetsDir)
	}

	cfg := &conf.Config{}
	if err := cfg.LoadJSONConfig(jsonConfig); err != nil {
		return false
	}

	inst, err := core.New(cfg.Build)
	if err != nil {
		return false
	}
	if err := inst.Start(); err != nil {
		_ = inst.Close()
		return false
	}

	x.instance = inst
	x.stopCh = make(chan struct{})
	return true
}

// AwaitExit blocks until Stop is called. Runs on a dedicated Kotlin thread.
func (x *XrayCore) AwaitExit() {
	x.mu.Lock()
	ch := x.stopCh
	x.mu.Unlock()
	if ch != nil {
		<-ch
	}
}

// Stop tears the running instance down.
func (x *XrayCore) Stop() {
	x.mu.Lock()
	inst := x.instance
	ch := x.stopCh
	x.instance = nil
	x.stopCh = nil
	x.mu.Unlock()

	if inst != nil {
		_ = inst.Close()
	}
	if ch != nil {
		close(ch)
	}
}
