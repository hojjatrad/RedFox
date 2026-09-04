// Package xray is the RedFox Xray mobile shim — a tiny gomobile-friendly
// wrapper around Xray-core.
//
// Built into an AAR with:
//   gomobile bind -javapkg=com.redfox -target=android/arm64 .
// producing class com.redfox.xray.Xraycore with instance field `xray`.
package xray

import (
	"os"
	"strings"
	"sync"

	"github.com/xtls/xray-core/core"
	"github.com/xtls/xray-core/infra/conf/serial"
)

// XrayCore is the singleton gomobile exports.
type XrayCore struct {
	mu       sync.Mutex
	instance *core.Instance
	stopCh   chan struct{}
}

// Xray is the package-level singleton gomobile turns into the `xray` field.
var Xray = &XrayCore{}

// StartConfig boots an Xray instance from a full JSON configuration.
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

	cfg, err := serial.LoadJSONConfig(strings.NewReader(jsonConfig))
	if err != nil {
		return false
	}
	inst, err := core.New(cfg)
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

// AwaitExit blocks until Stop is called.
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
		select {
		case <-ch:
		default:
			close(ch)
		}
	}
}
