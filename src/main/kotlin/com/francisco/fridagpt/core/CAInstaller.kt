package com.francisco.fridagpt.core

import com.francisco.fridagpt.core.SSLBypassOrchestrator.PhaseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private val logger = KotlinLogging.logger {}

/**
 * Gerencia download e instalação de certificados CA no dispositivo
 */
class CAInstaller(
    private val deviceId: String? = null
) {
    companion object {
        private const val BURP_CERT_URL = "http://burp/cert"
        private const val DEVICE_DOWNLOAD_DIR = "/sdcard/Download"
        private const val DEVICE_SYSTEM_CERT_DIR = "/system/etc/security/cacerts"
    }

    /**
     * Setup completo: baixa, converte e instala certificado
     */
    suspend fun setupBurpCertificate(
        proxyHost: String = "127.0.0.1",
        proxyPort: Int = 8080
    ): CASetupResult =
        withContext(Dispatchers.IO) {
            logger.info { "Setting up Burp CA certificate..." }

            val result = CASetupResult()

            try {
                // 1. Verificar se Burp está rodando
                println("\n📡 Step 1: Checking if Burp Suite is running...")
                if (!checkBurpRunning(proxyHost, proxyPort)) {
                    println("❌ Burp Suite not detected on $proxyHost:$proxyPort")
                    println("\n💡 Start Burp Suite:")
                    println("   1. Open Burp Suite")
                    println("   2. Proxy → Options → Proxy Listeners")
                    println("   3. Ensure listener is on 127.0.0.1:8080")
                    println("   4. Check 'Running' is enabled")
                    result.burpRunning = false
                    return@withContext result
                }
                println("✅ Burp Suite is running")
                result.burpRunning = true

                // 2. Baixar certificado do Burp
                println("\n📥 Step 2: Downloading CA certificate from Burp...")
                val certFile = downloadBurpCert(proxyHost, proxyPort)
                if (certFile == null) {
                    println("❌ Failed to download certificate")
                    println("\n💡 Troubleshooting:")
                    println("   • Check if proxy is configured correctly")
                    println("   • Try accessing http://burp/cert in browser")
                    return@withContext result
                }
                println("✅ Certificate downloaded: ${certFile.absolutePath}")
                result.certDownloaded = true
                result.certFile = certFile

                println("\n📲 Step 3: Pushing certificate to device...")
                if (!pushCertToDevice(certFile, type = CertificateType.USER)) {
                    println("❌ Failed to push certificate to device")
                    println("\n💡 Check:")
                    println("   • Device is connected: adb devices")
                    println("   • USB debugging is enabled")
                    return@withContext result
                }

                // 3. Instruir instalação manual
                println("\n📱 Step 3: Installing certificate on device...")
                println("╔════════════════════════════════════════════════════════╗")
                println("║         Manual Installation Required                   ║")
                println("╚════════════════════════════════════════════════════════╝")
                println()
                println("Please follow these steps on your device:")
                println()
                println("1. Open Settings → Security")
                println("   (May be under: Security & Privacy, or Additional Settings)")
                println()
                println("2. Find and tap:")
                println("   • 'Install certificates'")
                println()
                println("3. Tap 'CA certificate' or 'Install from SD card'")
                println()
                println("4. Navigate to: Download folder")
                println()
                println("5. Select: ${certFile.name}")
                println()
                println("6. Enter device PIN/Password if prompted")
                println()
                println("7. Give it a name (e.g., 'Burp CA')")
                println()
                println("8. Tap 'OK' to install")
                println()

                print("Have you installed the certificate? (y/N): ")
                val installed = readLine()?.trim()?.lowercase()
                result.certInstalled = installed == "y" || installed == "yes"

                if (result.certInstalled) {
                    println("✅ Certificate marked as installed")

                    // 4. Testar conexão
                    println("\n🧪 Step 4: Testing proxy connection...")
                    if (testProxyConnection()) {
                        println("✅ Proxy connection working!")
                        result.proxyTested = true
                    } else {
                        println("⚠️  Could not verify proxy connection")
                        println("   This is normal - we'll test with the app")
                    }
                } else {
                    println("⚠️  Certificate not installed")
                    println("   You can install it later from: $DEVICE_DOWNLOAD_DIR/${certFile.name}")
                }

                println("\n🔄 Step 5: Becoming root...")
                startRoot()
                if (!checkRoot())
                    return@withContext result

                // 4. Remount device
                println("\n📲 Step 6: Remount device...")
                if (!remountDevice()) {
                    println("❌ Failed to remount device")
                    println("\n💡 Check:")
                    println("   • Device was started with -writable-system")
                    return@withContext result
                }

                // 3. Converter para formato Android
                println("\n🔄 Step 7: Converting certificate to Android format...")
                val androidCert = convertToAndroidFormat(certFile)
                if (androidCert == null) {
                    println("❌ Failed to convert certificate")
                    return@withContext result
                }
                println("✅ Certificate converted: ${androidCert.absolutePath}")
                result.certConverted = true
                result.certFile = androidCert

                // 4. Push para device
                println("\n📲 Step 8: Pushing certificate to system cert directory...")
                if (!pushCertToDevice(androidCert, type = CertificateType.SYSTEM)) {
                    println("❌ Failed to push certificate to device")
                    println("\n💡 Check:")
                    println("   • Device is connected: adb devices")
                    println("   • USB debugging is enabled")
                    return@withContext result
                }
                println("✅ Certificate pushed to device")
                result.certPushed = true

                println("\n📲 Step 9: Certificate permissions...")
                giveCertPermissions(androidCert)
                rebootDevice()

                print("Have you rebooted the device? (Y/n): ")
                readLine()?.trim()?.lowercase()

                println("\n🔄 Step 10: Becoming root...")
                startRoot()
                if (!checkRoot())
                    return@withContext result

                result

            } catch (e: Exception) {
                logger.error(e) { "CA setup failed: ${e.message}" }
                println("❌ Error: ${e.message}")
                result
            }
        }


    /**
     * Verifica se Burp está rodando
     */
    private fun checkBurpRunning(host: String, port: Int): Boolean {
        return try {
            // Tentar conectar no proxy
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 3000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Baixa certificado do Burp via proxy
     */
    private fun downloadBurpCert(proxyHost: String, proxyPort: Int): File? {
        return try {
            // Configurar proxy temporariamente
            System.setProperty("http.proxyHost", proxyHost)
            System.setProperty("http.proxyPort", proxyPort.toString())

            val url = URL(BURP_CERT_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) {
                logger.error { "Failed to download cert: ${connection.responseCode}" }
                return null
            }

            val certData = connection.inputStream.readBytes()
            connection.disconnect()

            // Limpar proxy settings
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")

            // Salvar em arquivo temporário
            val certFile = File.createTempFile("burp_ca_", ".der")
            certFile.writeBytes(certData)

            certFile

        } catch (e: Exception) {
            logger.error(e) { "Download failed: ${e.message}" }
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            null
        }
    }

    /**
     * Converte certificado para formato Android (PEM com hash)
     */
    private fun convertToAndroidFormat(derFile: File): File? {
        return try {
            // Ler certificado
            val cf = CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(derFile.inputStream()) as X509Certificate

            // Calcular hash do subject (formato Android)
            val subjectBytes = cert.subjectX500Principal.encoded
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(subjectBytes)
            val hashString = hash.joinToString("") { "%02x".format(it) }
            val subjectHash = hashString.substring(0, 8)

            // Criar arquivo PEM
            val pemFile = File.createTempFile("burp_ca_${subjectHash}_", ".pem")

            pemFile.bufferedWriter().use { writer ->
                writer.write("-----BEGIN CERTIFICATE-----\n")
                val encoded = java.util.Base64.getEncoder().encodeToString(cert.encoded)
                // Quebrar em linhas de 64 caracteres
                encoded.chunked(64).forEach { line ->
                    writer.write("$line\n")
                }
                writer.write("-----END CERTIFICATE-----\n")
            }

            // Renomear com hash correto (formato Android)
            val finalFile = File(pemFile.parent, "$subjectHash.0")
            pemFile.renameTo(finalFile)

            logger.info { "Certificate converted with hash: $subjectHash" }
            finalFile

        } catch (e: Exception) {
            logger.error(e) { "Conversion failed: ${e.message}" }
            null
        }
    }

    /**
     * Push certificado para device
     */
    private fun pushCertToDevice(
        certFile: File,
        type: CertificateType,
    ): Boolean {
        return try {
            var targetPath = if (type == CertificateType.USER)
                DEVICE_DOWNLOAD_DIR
            else
                DEVICE_SYSTEM_CERT_DIR

            targetPath += "/${certFile.name}"

            val cmd = buildAdbCommand("push", certFile.absolutePath, targetPath)

            val proc = ProcessBuilder(cmd).start()
            val exitCode = proc.waitFor()

            if (exitCode == 0) {
                logger.info { "Certificate pushed to: $targetPath" }
                true
            } else {
                false
            }

        } catch (e: Exception) {
            logger.error(e) { "Push failed: ${e.message}" }
            false
        }
    }

    enum class CertificateType {
        USER, SYSTEM,
    }

    private fun remountDevice(): Boolean {
        return try {
            val cmd = buildAdbCommand("remount")

            val proc = ProcessBuilder(cmd).start()
            val exitCode = proc.waitFor()

            if (exitCode == 0) {
                logger.info { "device remounted" }
                true
            } else {
                false
            }

        } catch (e: Exception) {
            logger.error(e) { "remount failed: ${e.message}" }
            false
        }
    }

    private fun giveCertPermissions(certFile: File): Boolean {
        return try {
            val cmd = buildAdbCommand("shell", "chmod", "644", "$DEVICE_SYSTEM_CERT_DIR/${certFile.name}")

            val proc = ProcessBuilder(cmd).start()
            val exitCode = proc.waitFor()

            if (exitCode == 0) {
                logger.info { "permission granted" }
                true
            } else {
                false
            }

        } catch (e: Exception) {
            logger.error(e) { "remount failed: ${e.message}" }
            false
        }
    }

    private fun rebootDevice(): Boolean {
        return try {
            val cmd = buildAdbCommand("reboot")

            val proc = ProcessBuilder(cmd).start()
            val exitCode = proc.waitFor()

            if (exitCode == 0) {
                logger.info { "device remounted" }
                true
            } else {
                false
            }

        } catch (e: Exception) {
            logger.error(e) { "remount failed: ${e.message}" }
            false
        }
    }

    private fun checkRoot(): Boolean {
        val commands = buildAdbCommand("shell", "id")
        return try {
            val proc = ProcessBuilder(commands).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    private fun startRoot(): Boolean {
        val commands = buildAdbCommand("root")
        return try {
            val proc = ProcessBuilder(commands).start()
            val exitCode = proc.waitFor()

            if (exitCode == 0) {
                logger.info { "device rooted" }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Testa conexão com proxy
     */
    private fun testProxyConnection(): Boolean {
        return try {
            val cmd = buildAdbCommand("shell", "ping", "-c", "1", "8.8.8.8")

            val proc = ProcessBuilder(cmd).start()
            val exitCode = proc.waitFor()

            exitCode == 0

        } catch (_: Exception) {
            false
        }
    }

    /**
     * Lista certificados instalados
     */
    suspend fun listInstalledCerts(): List<String> = withContext(Dispatchers.IO) {
        try {
            val cmd = buildAdbCommand(
                "shell", "ls", "/data/misc/user/0/cacerts-added"
            )

            val proc = ProcessBuilder(cmd).start()
            val output = BufferedReader(InputStreamReader(proc.inputStream)).readText()
            proc.waitFor()

            output.lines().filter { it.isNotBlank() }

        } catch (e: Exception) {
            logger.error(e) { "Failed to list certs" }
            emptyList()
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
}

/**
 * Resultado do setup de CA
 */
data class CASetupResult(
    var burpRunning: Boolean = false,
    var certDownloaded: Boolean = false,
    var certConverted: Boolean = false,
    var certPushed: Boolean = false,
    var certInstalled: Boolean = false,
    var proxyTested: Boolean = false,
    var certFile: File? = null
) {
    val isComplete: Boolean
        get() = burpRunning && certDownloaded && certPushed && certInstalled

    val isReady: Boolean
        get() = isComplete && (proxyTested || certInstalled)
}