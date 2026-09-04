/*
 * RedFox — minimal stub for the `aether` native library.
 *
 * The upstream Rust core (libaether.so) implements Cloudflare WARP transports
 * (MASQUE/WireGuard/WARP-on-WARP). RedFox's default and only self-hosted
 * transport is the Xray core; the WARP paths are removed from the picker.
 *
 * aether_jni.cpp links against these C symbols. Rather than compile the entire
 * Rust core for builds that don't need it, we ship this tiny C implementation
 * that links cleanly and reports a clear error if any WARP path is ever invoked.
 */
#include <stddef.h>

static const char *g_last_error =
    "RedFox: the WARP (Cloudflare) core is not built in this configuration. "
    "Use RedFox Xray.";

int aether_prepare_json(const char *json) { (void)json; return 0; }
int aether_zt_request_email_code(const char *team, const char *email) {
    (void)team; (void)email; return 0;
}
int aether_zt_confirm_email_code(const char *code) { (void)code; return 0; }
const char *aether_last_result(void) { return "{}"; }
int aether_start_json_with_tun(const char *json, int tun_fd) {
    (void)json; (void)tun_fd; return -1;
}
int aether_start_json(const char *json) { (void)json; return -1; }
int aether_stop(void) { return 0; }
int aether_is_running(void) { return 0; }
int aether_is_ready(void) { return 0; }
const char *aether_last_error(void) { return g_last_error; }
const char *aether_last_log(void) { return ""; }
void aether_set_socket_protector(int (*protector)(int)) { (void)protector; }
void aether_set_event_callback(void (*callback)(const char *)) { (void)callback; }
