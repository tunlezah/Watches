package io.github.tunlezah.watches.search

import org.koin.core.module.Module
import org.koin.dsl.module
import java.net.NetworkInterface
import java.security.KeyPairGenerator
import javax.net.ssl.SSLContext

/**
 * Reports offline only when no network interface has a routable address, which is what a Mac looks
 * like with Wi-Fi off and no cable. Otherwise it says online and lets the request decide, as the
 * iPhone does before its first path update. It only reads the machine's own interfaces: nothing is sent.
 */
class JvmConnectivity : Connectivity {
    override fun isOnline(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces().asSequence().any { nic ->
            nic.isUp && !nic.isLoopback && nic.inetAddresses.asSequence().any { !it.isLoopbackAddress && !it.isLinkLocalAddress }
        }
    }.getOrDefault(true)
}

/**
 * What this Java runtime can do for Lookup's connections, as a sentence; throws if it can't make
 * them. A packaged desktop app carries a trimmed runtime, where modern TLS can go missing without
 * anything else noticing, so the app's self-test asks. It connects to nothing.
 */
fun describeSecureConnections(): String {
    val engine = SSLContext.getDefault().createSSLEngine()
    require("TLSv1.3" in engine.supportedProtocols) { "No TLS 1.3: ${engine.supportedProtocols.toList()}" }
    require(engine.supportedCipherSuites.any { "_ECDHE_" in it }) { "No elliptic-curve cipher suites" }
    KeyPairGenerator.getInstance("EC")
    return "TLS 1.3 with elliptic-curve keys"
}

fun jvmSearchModule(): Module = module {
    single<Connectivity> { JvmConnectivity() }
}
