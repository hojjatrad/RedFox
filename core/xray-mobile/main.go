// Package redfoxxray is the RedFox Xray mobile shim — a tiny gomobile-friendly
// wrapper around Xray-core.
//
// It is built into libxray.aar by core/xray-mobile/build-aar.sh (also wired into
// the GitHub Actions build). Kotlin reaches it through XrayBridge.kt as the
// class com.redfox.xray.XrayCore with three methods:
//
//   StartConfig(jsonConfig, assetsDir string) bool
//   AwaitExit()                       // blocks until Stop()
//   Stop()
//
// Everything functional (SOCKS inbound, DNS, routing, the user's vless/vmess/
// trojan/ss outbound) is described by the JSON config built on the Kotlin side;
// this file only boots the core.
package redfoxxray

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

var instance = &XrayCore{}

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
