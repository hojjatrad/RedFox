package com.msnguard.vpn.xray

import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * دیمن محلی سازگار با پروتکل «udpgw» پروژه‌ی badvpn.
 *
 * چرا لازم است: لایه‌ی tun2socks (lwIP) بسته‌های UDP را روی اتصال SOCKS با
 * قاب‌بندی اختصاصی udpgw می‌فرستد و پاسخ هم‌شکل می‌خواهد. در مسیر Psiphon این
 * کار را خودِ سرور Psiphon انجام می‌دهد؛ برای Xray ما چنین چیزی آن‌طرف نداریم،
 * پس یک دیمن محلی روی 127.0.0.1:7300 می‌نشانیم که:
 *
 *   1. به‌عنوان سرور SOCKS5 تونل tun2socks را می‌پذیرد (فقط CONNECT).
 *   2. فریم‌های udpgw را می‌خواند (مقصد IPv4/IPv6 + دیتاگرام).
 *   3. برای هر اتصال tun2socks یک SOCKS5 UDP ASSOCIATE به هسته‌ی Xray می‌گیرد
 *      و دیتاگرام‌ها را کپسوله‌شده به رله‌ی UDP اِکس‌رِی می‌فرستد؛ پاسخ‌ها را
 *      از سوکت UDP می‌گیرد و با همان conid روی استریم TCP برمی‌گرداند.
 *
 * فریم (little-endian، مطابق badvpn/protocol/udpgw_proto.h و UdpGwClient.c):
 *   flags:1  conid:2(LE)
 *   اگر KEEPALIVE(0x01) باشد همین ۳ بایت است؛ وگرنه
 *   IPv4 → ip:4(BE) port:2(BE)   |   IPv6(0x08) → ip:16 port:2
 *   سپس payload دیتاگرام.
 */
object UdpGwServer {

    const val UDPGW_PORT = 7300

    private const val FLAG_KEEPALIVE = 0x01
    private const val FLAG_IPV6 = 0x08
    private const val HDR = 3
    private const val IPV4_A = 6
    private const val IPV6_A = 18

    @Volatile private var running = false
    private var serverSocket: ServerSocket? = null

    /** @param xraySocksPort پورت SOCKS هسته‌ی Xray. */
    fun start(xraySocksPort: Int): Boolean {
        if (running) stop()
        return try {
            val ss = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress("127.0.0.1", UDPGW_PORT))
            }
            serverSocket = ss
            running = true
            Thread({ acceptLoop(ss, xraySocksPort) }, "udpgw-accept").apply { isDaemon = true }.start()
            true
        } catch (e: Exception) {
            ConnectionLog.record("udpgw start failed: ${e.message}")
            false
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun acceptLoop(ss: ServerSocket, xrayPort: Int) {
        while (running) {
            val client = try { ss.accept() } catch (e: Exception) { if (running) ConnectionLog.record("udpgw accept: ${e.message}"); return }
            Thread({ handleTun2SocksClient(client, xrayPort) }, "udpgw-conn").apply { isDaemon = true }.start()
        }
    }

    private class Frame(
        val flags: Int, val conid: Int, val ipv6: Boolean,
        val addr: ByteArray, val port: Int, val payload: ByteArray,
    ) { val keepalive get() = (flags and FLAG_KEEPALIVE) != 0 }

    /** هر اتصال tun2socks → یک نمونه‌ی این کلاس (یک UDP ASSOCIATE مشترک). */
    private fun handleTun2SocksClient(client: Socket, xrayPort: Int) {
        var ctrl: Socket? = null
        var udp: DatagramSocket? = null
        val conTargets = HashMap<Int, Pair<ByteArray, Int>>()   // conid -> (addr, port)

        try {
            client.use { sock ->
                sock.tcpNoDelay = true
                val input = DataInputStream(sock.getInputStream().buffered())
                val output = sock.getOutputStream()

                // ---- SOCKS5 handshake (no auth) ----
                if (input.ub() != 5) return
                val n = input.ub() ?: return
                repeat(n) { input.ub() }
                output.write(byteArrayOf(0x05, 0x00)); output.flush()

                // ---- CONNECT request (به مقصد udpgw) ----
                if (input.ub() != 5) return
                input.ub()            // CMD
                input.ub()            // RSV
                when (input.ub()) {   // ATYP
                    1 -> repeat(4) { input.ub() }
                    3 -> { val l = input.ub() ?: return; repeat(l) { input.ub() } }
                    4 -> repeat(16) { input.ub() }
                    else -> return
                }
                input.ub(); input.ub()   // port
                output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); output.flush()

                // ---- یک UDP ASSOCIATE مشترک به هسته‌ی Xray ----
                val relay = openUdpRelay(xrayPort) ?: run {
                    ConnectionLog.record("udpgw: Xray UDP relay unavailable; UDP will fail")
                    null
                }
                if (relay != null) {
                    ctrl = relay.control
                    udp = relay.udpSocket
                    // رشته‌ی دریافت پاسخ از رله‌ی Xray
                    Thread({
                        val rx = ByteArray(32 * 1024)
                        while (running && relay.control.isConnected && !relay.control.isClosed) {
                            val dp = DatagramPacket(rx, rx.size)
                            try { relay.udpSocket.receive(dp) } catch (e: Exception) { break }
                            val stripped = stripSocksUdp(rx, dp.length) ?: continue
                            val target = stripSocksUdpAddr(rx, dp.length)
                            // پیدا کردن conid با مقصد (همان مقصدی که کلاینت خواسته بود)
                            val conid = conTargets.entries.firstOrNull {
                                it.second.first.contentEquals(target.first) && it.second.second == target.second
                            }?.key
                                // فال‌بک: آخرین conid (اکثر ترافیک DNS عملاً یکی است)
                                ?: conTargets.keys.lastOrNull() ?: continue
                            try {
                                synchronized(output) {
                                    output.write(buildReplyFrame(conid, target.first, target.second, stripped))
                                    output.flush()
                                }
                            } catch (e: Exception) { break }
                        }
                    }, "udpgw-rx").apply { isDaemon = true }.start()
                }

                // ---- حلقه‌ی فریم‌های tun2socks → ما ----
                while (running) {
                    val frame = readFrame(input) ?: break
                    if (frame.keepalive) { continue }
                    // نگاشت conid به مقصدی که کلاینت واقعاً خواسته (برای پاسخ).
                    conTargets[frame.conid] = frame.addr to frame.port
                    if (udp != null) {
                        // DNS شفاف: وقتی مقصد، آدرس روترِ TUN است (مثلاً 10.0.0.2:53
                        // که lwIP به‌عنوان resolver از آن استفاده می‌کند) آن را به
                        // رزولور عمومیِ داخلِ تونل می‌فرستیم؛ پاسخ را هم با همان
                        // conid برمی‌گردانیم تا لایه‌ی TUN آن را بپذیرد.
                        val (dstAddr, dstPort) = dnsRewrite(frame.addr, frame.port)
                        val pkt = wrapSocksUdp(dstAddr, dstPort, frame.payload, relay!!.relayPort)
                        try { udp.send(pkt) } catch (e: Exception) { }
                    }
                }
            }
        } catch (e: Exception) {
            // بسته/ریست شدن اتصال هنگام teardown طبیعی است
        } finally {
            runCatching { udp?.close() }
            runCatching { ctrl?.close() }
        }
    }

    private class Relay(val control: Socket, val udpSocket: DatagramSocket, val relayPort: Int)

    private fun openUdpRelay(xrayPort: Int): Relay? {
        return try {
            val ctrl = Socket()
            ctrl.tcpNoDelay = true
            ctrl.connect(InetSocketAddress("127.0.0.1", xrayPort), 5_000)
            val cin = ctrl.getInputStream().buffered()
            val cout = ctrl.getOutputStream()
            cout.write(byteArrayOf(0x05, 0x01, 0x00)); cout.flush()
            cin.read(ByteArray(2))                                   // 05 00
            cout.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); cout.flush() // UDP ASSOCIATE
            val reply = ByteArray(10)
            var off = 0
            while (off < 10) { val n = cin.read(reply, off, 10 - off); if (n < 0) return null; off += n }
            if (reply[1] != 0x00.toByte()) { ConnectionLog.record("udpgw: Xray UDP ASSOCIATE refused"); return null }
            val port = (((reply[8].toInt() and 0xFF) shl 8) or (reply[9].toInt() and 0xFF)).let {
                if (it == 0) xrayPort else it
            }
            val ds = DatagramSocket().apply { soTimeout = 0; connect(InetSocketAddress("127.0.0.1", port)) }
            Relay(ctrl, ds, port)
        } catch (e: Exception) {
            ConnectionLog.record("udpgw relay setup failed: ${e.message}")
            null
        }
    }

    private fun readFrame(input: DataInputStream): Frame? {
        val flags = input.ub() ?: return null
        val hi = input.ub() ?: return null
        val lo = input.ub() ?: return null
        val conid = (hi shl 8) or lo
        if ((flags and FLAG_KEEPALIVE) != 0) return Frame(flags, conid, false, ByteArray(0), 0, ByteArray(0))
        val ipv6 = (flags and FLAG_IPV6) != 0
        val addr = ByteArray(if (ipv6) 16 else 4)
        input.readFullySafe(addr) ?: return null
        val ph = input.ub() ?: return null
        val pl = input.ub() ?: return null
        val port = (ph shl 8) or pl
        val payload = readPayload(input) ?: return null
        return Frame(flags, conid, ipv6, addr, port, payload)
    }

    /** بدوپن هر دیتاگرام را یک‌جا می‌نویسد؛ payload تا مرز فریم بعدی خوانده می‌شود. */
    private fun readPayload(input: InputStream): ByteArray? {
        val first = input.read()
        if (first < 0) return null
        val avail = try { input.available() } catch (e: Exception) { 0 }
        val rest = avail.coerceIn(0, 2047)
        val out = ByteArray(1 + rest)
        out[0] = first.toByte()
        if (rest > 0) {
            var got = 0
            while (got < rest) {
                val n = input.read(out, 1 + got, rest - got); if (n < 0) break
                got += n
            }
            if (got < rest) return out.copyOf(1 + got)
        }
        return out
    }

    private fun wrapSocksUdp(addr: ByteArray, port: Int, payload: ByteArray, relayPort: Int): DatagramPacket {
        val hdr = socksUdpHeader(addr, port)
        val buf = ByteBuffer.allocate(hdr.size + payload.size).order(ByteOrder.BIG_ENDIAN)
        buf.put(hdr); buf.put(payload)
        return DatagramPacket(buf.array(), buf.array().size, InetSocketAddress("127.0.0.1", relayPort))
    }

    private fun buildReplyFrame(conid: Int, addr: ByteArray, port: Int, payload: ByteArray): ByteArray {
        val ipv6 = addr.size == 16
        val total = HDR + (if (ipv6) IPV6_A else IPV4_A) + payload.size
        val bb = ByteBuffer.allocate(total)
        bb.order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0.toByte())
        bb.putShort(conid.toShort())
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.put(addr)
        bb.putShort(port.toShort())
        bb.put(payload)
        return bb.array()
    }

    private fun socksUdpHeader(addr: ByteArray, port: Int): ByteArray {
        val bb = ByteBuffer.allocate(if (addr.size == 4) 10 else 22).order(ByteOrder.BIG_ENDIAN)
        bb.put(0); bb.put(0); bb.put(0)
        if (addr.size == 4) { bb.put(1); bb.put(addr) } else { bb.put(4); bb.put(addr) }
        bb.putShort(port.toShort())
        return bb.array()
    }

    private fun stripSocksUdp(data: ByteArray, len: Int): ByteArray? {
        if (len < 10) return null
        val bb = ByteBuffer.wrap(data, 0, len).order(ByteOrder.BIG_ENDIAN)
        bb.short; bb.get()
        val atyp = bb.get().toInt() and 0xFF
        when (atyp) {
            1 -> bb.position(bb.position() + 4)
            4 -> bb.position(bb.position() + 16)
            3 -> { val l = bb.get().toInt() and 0xFF; bb.position(bb.position() + l) }
            else -> return null
        }
        bb.short
        return data.copyOfRange(bb.position(), len)
    }

    private fun stripSocksUdpAddr(data: ByteArray, len: Int): Pair<ByteArray, Int> {
        val bb = ByteBuffer.wrap(data, 0, len).order(ByteOrder.BIG_ENDIAN)
        bb.short; bb.get()
        val atyp = bb.get().toInt() and 0xFF
        val addr = when (atyp) {
            1 -> ByteArray(4).also { bb.get(it) }
            4 -> ByteArray(16).also { bb.get(it) }
            3 -> { val l = bb.get().toInt() and 0xFF; ByteArray(l).also { bb.get(it) } }
            else -> ByteArray(0)
        }
        val port = bb.short.toInt() and 0xFFFF
        return addr to port
    }

    /**
     * آدرس‌های روتر TUN که lwIP به‌عنوان resolver به آن‌ها DNS می‌فرستد
     * (Tun2SocksManager.PrivateAddress.router). چون آن آدرس‌ها واقعاً روی
     * شبکه نیستند، پرس‌وجوی پورت ۵۳ به این مقصدها را به 1.1.1.1:53 داخل تونل
     * هدایت می‌کنیم.
     */
    private val dnsRouters = setOf("10.0.0.2", "172.16.0.2", "192.168.0.2", "169.254.1.2")

    private fun dnsRewrite(addr: ByteArray, port: Int): Pair<ByteArray, Int> {
        if (port == 53 && addr.size == 4) {
            val ip = ((addr[0].toInt() and 0xFF).toString() + "." +
                (addr[1].toInt() and 0xFF) + "." +
                (addr[2].toInt() and 0xFF) + "." +
                (addr[3].toInt() and 0xFF))
            if (ip in dnsRouters) {
                return byteArrayOf(1, 1, 1, 1) to 53
            }
        }
        return addr to port
    }

    private fun DataInputStream.ub(): Int? = try { val v = read(); if (v < 0) null else v } catch (e: Exception) { null }
    private fun DataInputStream.readFullySafe(b: ByteArray): Boolean = try { readFully(b); true } catch (e: Exception) { false }
}
