package com.francisco.fridagpt.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.InputStreamReader

private val logger = KotlinLogging.logger {}

/**
 * Gerencia configuração de proxy no dispositivo Android
 */
class ProxyManager(
    private val deviceId: String? = null
) {
    companion object {
        private const val DEFAULT_PROXY_HOST = "127.0.0.1"
        private const val DEFAULT_PROXY_PORT = 8080
    }

    /**
     * Configura proxy HTTP no dispositivo
     */
    suspend fun setupProxy(
        host: String = DEFAULT_PROXY_HOST,
        port: Int = DEFAULT_PROXY_PORT
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.info { "Setting up proxy: $host:$port" }

            // 1. Configurar proxy global no Android
            val settingsCmd = buildAdbCommand(
                "shell", "settings", "put", "global", "http_proxy", "$host:$port"
            )

            if (!executeCommand(settingsCmd)) {
                logger.error { "Failed to set http_proxy setting" }
                return@withContext false
            }

            logger.info { "✅ Proxy configured on device" }

            // 2. Setup port forwarding (reverse) para localhost
            if (host == "127.0.0.1" || host == "localhost") {
                val reverseCmd = buildAdbCommand("reverse", "tcp:$port", "tcp:$port")

                if (!executeCommand(reverseCmd)) {
                    logger.warn { "Failed to setup reverse port forwarding (may not be needed)" }
                } else {
                    logger.info { "✅ Port forwarding configured: tcp:$port -> tcp:$port" }
                }
            }

            // 3. Verificar configuração
            val currentProxy = getCurrentProxy()
            logger.info { "Current proxy setting: $currentProxy" }

            true

        } catch (e: Exception) {
            logger.error(e) { "Failed to setup proxy: ${e.message}" }
            false
        }
    }

    /**
     * Remove configuração de proxy
     */
    suspend fun removeProxy(): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.info { "Removing proxy configuration..." }

            // Remover proxy setting
            val cmd = buildAdbCommand(
                "shell", "settings", "delete", "global", "http_proxy"
            )

            if (!executeCommand(cmd)) {
                // Tentar alternativa: configurar como :0 (desabilita)
                val altCmd = buildAdbCommand(
                    "shell", "settings", "put", "global", "http_proxy", ":0"
                )
                executeCommand(altCmd)
            }

            logger.info { "✅ Proxy configuration removed" }
            true

        } catch (e: Exception) {
            logger.error(e) { "Failed to remove proxy: ${e.message}" }
            false
        }
    }

    /**
     * Obtém configuração atual de proxy
     */
    suspend fun getCurrentProxy(): String? = withContext(Dispatchers.IO) {
        try {
            val cmd = buildAdbCommand("shell", "settings", "get", "global", "http_proxy")

            val output = executeCommandWithOutput(cmd)
            return@withContext output?.trim()?.takeIf { it != "null" && it.isNotEmpty() }

        } catch (e: Exception) {
            logger.error(e) { "Failed to get current proxy: ${e.message}" }
            null
        }
    }

    /**
     * Verifica se proxy está ativo
     */
    suspend fun isProxyActive(): Boolean {
        val current = getCurrentProxy()
        return current != null && current != ":0"
    }

    /**
     * Testa conectividade com o proxy
     */
    suspend fun testProxyConnectivity(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // Tentar conectar via adb shell
            val cmd = buildAdbCommand("shell", "nc", "-z", host, port.toString())

            val success = executeCommand(cmd, timeout = 5000)

            if (success) {
                logger.info { "✅ Proxy is reachable at $host:$port" }
            } else {
                logger.warn { "⚠️  Cannot reach proxy at $host:$port" }
            }

            success

        } catch (e: Exception) {
            logger.error(e) { "Proxy connectivity test failed" }
            false
        }
    }

    /**
     * Setup completo para SSL Pinning bypass
     */
    suspend fun setupForSSLBypass(
        proxyHost: String = DEFAULT_PROXY_HOST,
        proxyPort: Int = DEFAULT_PROXY_PORT
    ): SSLProxySetup {
        logger.info { "Setting up environment for SSL Pinning bypass..." }

        val results = SSLProxySetup(
            proxyConfigured = setupProxy(proxyHost, proxyPort),
            proxyReachable = testProxyConnectivity(proxyHost, proxyPort),
            proxyHost = proxyHost,
            proxyPort = proxyPort
        )

        if (results.isReady) {
            logger.info { "✅ Environment ready for SSL bypass testing" }
            printInstructions(proxyHost, proxyPort)
        } else {
            logger.error { "❌ Failed to setup environment properly" }
            printTroubleshooting(results)
        }

        return results
    }

    /**
     * Cleanup após testes
     */
    suspend fun cleanup() {
        logger.info { "Cleaning up proxy configuration..." }
        removeProxy()

        // Remover reverse port forwarding
        try {
            val cmd = buildAdbCommand("reverse", "--remove-all")
            executeCommand(cmd)
        } catch (e: Exception) {
            logger.debug { "Reverse cleanup: ${e.message}" }
        }
    }

    private fun buildAdbCommand(vararg args: String): List<String> {
        return buildList {
            add("adb")
            deviceId?.let {
                add("-s")
                add(it)
            }
            addAll(args)
        }
    }

    private fun executeCommand(command: List<String>, timeout: Long = 10000): Boolean {
        return try {
            val proc = ProcessBuilder(command).start()
            val completed = proc.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)

            if (!completed) {
                proc.destroyForcibly()
                return false
            }

            proc.exitValue() == 0

        } catch (e: Exception) {
            logger.error(e) { "Command execution failed: ${command.joinToString(" ")}" }
            false
        }
    }

    private fun executeCommandWithOutput(command: List<String>): String? {
        return try {
            val proc = ProcessBuilder(command).start()
            val output = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            proc.waitFor()

            if (proc.exitValue() == 0) output else null

        } catch (e: Exception) {
            logger.error(e) { "Command output failed" }
            null
        }
    }

    private fun printInstructions(host: String, port: Int) {
        println("""
        
        ╔════════════════════════════════════════════════════════╗
        ║         SSL Pinning Bypass - Proxy Setup              ║
        ╚════════════════════════════════════════════════════════╝
        
        ✅ Proxy configured on device: $host:$port
        
        📝 Next Steps:
        
        1. Start your proxy tool (Burp/mitmproxy):
           • Burp: Proxy → Options → Listen on 127.0.0.1:$port
           • mitmproxy: mitmproxy -p $port
        
        2. Install CA certificate on device:
           • Burp: http://burp/cert
           • mitmproxy: ~/.mitmproxy/mitmproxy-ca-cert.cer
           • adb push cert.cer /sdcard/
           • Settings → Security → Install from storage
        
        3. Run the SSL bypass script (will be executed automatically)
        
        4. Test the app and check proxy for HTTPS traffic
        
        ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        
        """.trimIndent())
    }

    private fun printTroubleshooting(setup: SSLProxySetup) {
        println("""
        
        ❌ Proxy Setup Issues:
        
        ${if (!setup.proxyConfigured) "✗ Proxy configuration failed" else "✓ Proxy configured"}
        ${if (!setup.proxyReachable) "✗ Proxy not reachable" else "✓ Proxy reachable"}
        
        Troubleshooting:
        1. Check if adb is working: adb devices
        2. Verify device is connected
        3. Start your proxy tool (Burp/mitmproxy) on port ${setup.proxyPort}
        4. Check firewall settings
        
        """.trimIndent())
    }
}

/**
 * Resultado do setup de proxy para SSL bypass
 */
data class SSLProxySetup(
    val proxyConfigured: Boolean,
    val proxyReachable: Boolean,
    val proxyHost: String,
    val proxyPort: Int
) {
    val isReady: Boolean
        get() = proxyConfigured && proxyReachable
}
