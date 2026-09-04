package ca.psiphon

import android.content.Context

/**
 * Compile-time stub for the Psiphon tunnel Go binding.
 *
 * The real Psiphon library is a gomobile-built AAR (libgojni.so + go.Seq runtime),
 * and a gomobile AAR cannot coexist with the Xray gomobile AAR in one APK (both ship
 * the same `go.*` classes and an identically-named `libgojni.so`). RedFox uses Xray as
 * its core, so the Psiphon path is disabled at build time. This stub preserves the
 * exact API surface that [com.msnguard.vpn.MsnGuardVpnService] compiles against; every
 * method is a safe no-op / failing default that is never reached in the Xray flow.
 */
class PsiphonTunnel private constructor(@Suppress("unused") private val hostService: HostService?) {

    class Exception(message: String? = null, cause: Throwable? = null) : kotlin.Exception(message, cause)

    interface HostLogger {
        fun onDiagnosticMessage(message: String)
    }

    interface HostLibraryLoader {
        fun skipHostLibraryLoad(): Boolean = true
    }

    interface HostService : HostLogger, HostLibraryLoader {
        fun getContext(): Context?
        fun getPsiphonConfig(): String?
        fun bindToDevice(fileDescriptor: Long) {}
        fun onAvailableEgressRegions(regions: MutableList<String>?) {}
        fun onSocksProxyPortInUse(port: Int) {}
        fun onHttpProxyPortInUse(port: Int) {}
        fun onListeningSocksProxyPort(port: Int) {}
        fun onListeningHttpProxyPort(port: Int) {}
        fun onListeningSocksProxyUnixPath(path: String?) {}
        fun onListeningHttpProxyUnixPath(path: String?) {}
        fun onUpstreamProxyError(message: String?) {}
        fun onConnecting() {}
        fun onConnected() {}
        fun onHomepage(url: String?) {}
        fun onClientRegion(region: String?) {}
        fun onClientAddress(address: String?) {}
        fun onClientUpgradeDownloaded(filename: String?) {}
        fun onClientIsLatestVersion() {}
        fun onSplitTunnelRegions(regions: MutableList<String>?) {}
        fun onUntunneledAddress(address: String?) {}
        fun onBytesTransferred(sent: Long, received: Long) {}
        fun onStartedWaitingForNetworkConnectivity() {}
        fun onStoppedWaitingForNetworkConnectivity() {}
        fun onActiveAuthorizationIDs(activeAuthorizationIDs: MutableList<String>?) {}
        fun onTrafficRateLimits(upstreamBytesPerSecond: Long, downstreamBytesPerSecond: Long) {}
        fun onConnectedServerRegion(region: String?) {}
        fun onExiting() {}
    }

    fun setVpnMode(vpnMode: Boolean) {}

    @Throws(Exception::class)
    fun startTunneling(config: String?) {
        throw Exception("RedFox uses Xray as its core; Psiphon is not bundled in this build.")
    }

    fun stop() {}

    @Throws(Exception::class)
    fun restartPsiphon() {}

    @Throws(Exception::class)
    fun reconnectPsiphon() {}

    fun appResumed() {}

    fun setClientPlatformAffixes(clientPlatformAffixes: String?, legacyClientPlatformAffixes: String?) {}

    fun exportExchangePayload(): String? = null

    fun importExchangePayload(payload: String?): Boolean = false

    fun importPushPayload(payload: ByteArray?): Boolean = false

    fun writeRuntimeProfiles(outputPath: String?, tunnelPid: Int, networkId: Int) {}

    fun getLocalSocksProxyPort(): Int = 0

    companion object {
        @JvmStatic
        fun newPsiphonTunnel(hostService: HostService?): PsiphonTunnel = PsiphonTunnel(hostService)

        @JvmStatic
        fun getDefaultUpgradeDownloadFilePath(context: Context?): String? = null

        @JvmStatic
        fun getUpgradeDownloadFilePath(filename: String?): String? = null
    }
}
