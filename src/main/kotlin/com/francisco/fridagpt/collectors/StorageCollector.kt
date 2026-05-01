package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.DatabaseInfo
import com.francisco.fridagpt.models.FileInfo
import com.francisco.fridagpt.models.Severity
import com.francisco.fridagpt.models.SharedPreferencesInfo
import com.francisco.fridagpt.models.StorageInfo
import com.francisco.fridagpt.models.StorageVulnerability
import com.francisco.fridagpt.models.VulnerabilityType
import com.francisco.fridagpt.utils.ScriptLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Coleta informações sobre armazenamento do app e detecta possíveis vulnerabilidades
 */
class StorageCollector(
    private val connector: FridaConnector
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Coleta informações completas de armazenamento
     */
    suspend fun collect(): StorageInfo? {
        logger.info { "Collecting storage information..." }

        val script = ScriptLoader.load("frida-scripts/collectors/storage.js")
        val rawOutput = connector.executeCollectorScript(script) ?: return null

        return try {
            val jsonStart = rawOutput.indexOf('{')
            val jsonEnd = rawOutput.lastIndexOf('}') + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.warn { "No JSON found in storage output" }
                return null
            }

            val jsonStr = rawOutput.substring(jsonStart, jsonEnd)
            val result = json.decodeFromString<StorageResult>(jsonStr)

            // Analisar vulnerabilidades
            val vulnerabilities = analyzeVulnerabilities(result)

            StorageInfo(
                databases = result.databases.map {
                    DatabaseInfo(it.name, it.path, it.tables)
                },
                sharedPreferences = result.sharedPreferences.map {
                    SharedPreferencesInfo(
                        name = it.name,
                        path = it.path,
                        keys = it.keys,
                        hasSensitiveData = detectSensitiveKeys(it.keys)
                    )
                },
                filesDirectory = result.filesDir,
                internalFiles = result.internalFiles,
                externalFiles = result.externalFiles,
                vulnerabilities = vulnerabilities
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse storage info: ${e.message}" }
            null
        }
    }

    /**
     * Detecta chaves sensíveis em SharedPreferences
     */
    private fun detectSensitiveKeys(keys: List<String>): Boolean {
        val sensitivePatterns = listOf(
            "password", "passwd", "pwd",
            "token", "auth", "secret",
            "api_key", "apikey", "key",
            "session", "cookie",
            "credit", "card", "cvv",
            "pin", "otp",
            "private", "credential"
        )

        return keys.any { key ->
            val lowerKey = key.lowercase()
            sensitivePatterns.any { pattern -> lowerKey.contains(pattern) }
        }
    }

    /**
     * Analisa vulnerabilidades de armazenamento
     */
    private fun analyzeVulnerabilities(result: StorageResult): List<StorageVulnerability> {
        val vulnerabilities = mutableListOf<StorageVulnerability>()

        // 1. SharedPreferences com dados sensíveis
        result.sharedPreferences.forEach { prefs ->
            val sensitiveKeys = prefs.keys.filter { key ->
                val lower = key.lowercase()
                listOf("password", "token", "secret", "api_key", "pin").any {
                    lower.contains(it)
                }
            }

            if (sensitiveKeys.isNotEmpty()) {
                vulnerabilities.add(
                    StorageVulnerability(
                        type = VulnerabilityType.INSECURE_SHARED_PREFERENCES,
                        severity = Severity.HIGH,
                        description = "Sensitive data stored in SharedPreferences: ${prefs.name}",
                        details = "Keys: ${sensitiveKeys.joinToString(", ")}",
                        recommendation = "Use Android Keystore or EncryptedSharedPreferences",
                        location = prefs.path
                    )
                )
            }
        }

        // 2. Databases não criptografadas
        result.databases.forEach { db ->
            if (!db.name.contains("encrypted", ignoreCase = true)) {
                vulnerabilities.add(
                    StorageVulnerability(
                        type = VulnerabilityType.UNENCRYPTED_DATABASE,
                        severity = Severity.MEDIUM,
                        description = "Unencrypted SQLite database: ${db.name}",
                        details = "Tables: ${db.tables.joinToString(", ")}",
                        recommendation = "Use SQLCipher for database encryption",
                        location = db.path
                    )
                )
            }
        }

        // 3. Arquivos em external storage
        if (result.externalFiles.isNotEmpty()) {
            vulnerabilities.add(
                StorageVulnerability(
                    type = VulnerabilityType.EXTERNAL_STORAGE_USAGE,
                    severity = Severity.LOW,
                    description = "Files stored in external storage",
                    details = "${result.externalFiles.size} files found",
                    recommendation = "Avoid external storage for sensitive data",
                    location = "External storage"
                )
            )
        }

        // 4. Arquivos com nomes suspeitos em internal storage
        val suspiciousFiles = result.internalFiles.filter { file ->
            val name = file.name.lowercase()
            listOf("backup", "export", "dump", "log", "temp").any { name.contains(it) }
        }

        if (suspiciousFiles.isNotEmpty()) {
            vulnerabilities.add(
                StorageVulnerability(
                    type = VulnerabilityType.SUSPICIOUS_FILES,
                    severity = Severity.LOW,
                    description = "Suspicious files in internal storage",
                    details = suspiciousFiles.joinToString(", ") { it.name },
                    recommendation = "Review file contents for sensitive data",
                    location = result.filesDir
                )
            )
        }

        return vulnerabilities
    }

    /**
     * Gera relatório de segurança de armazenamento
     */
    fun generateSecurityReport(storageInfo: StorageInfo): String {
        return buildString {
            appendLine("╔════════════════════════════════════════════════╗")
            appendLine("║      STORAGE SECURITY ANALYSIS REPORT          ║")
            appendLine("╚════════════════════════════════════════════════╝")
            appendLine()

            // Resumo
            appendLine("📊 SUMMARY")
            appendLine("  Databases: ${storageInfo.databases.size}")
            appendLine("  SharedPreferences: ${storageInfo.sharedPreferences.size}")
            appendLine("  Internal Files: ${storageInfo.internalFiles.size}")
            appendLine("  External Files: ${storageInfo.externalFiles.size}")
            appendLine("  Vulnerabilities: ${storageInfo.vulnerabilities.size}")
            appendLine()

            // Vulnerabilidades por severidade
            val high = storageInfo.vulnerabilities.count { it.severity == Severity.HIGH }
            val medium = storageInfo.vulnerabilities.count { it.severity == Severity.MEDIUM }
            val low = storageInfo.vulnerabilities.count { it.severity == Severity.LOW }

            appendLine("🔴 HIGH:   $high")
            appendLine("🟡 MEDIUM: $medium")
            appendLine("🟢 LOW:    $low")
            appendLine()

            // Detalhes das vulnerabilidades
            if (storageInfo.vulnerabilities.isNotEmpty()) {
                appendLine("⚠️  VULNERABILITIES FOUND")
                appendLine("━".repeat(50))

                storageInfo.vulnerabilities.forEachIndexed { index, vuln ->
                    appendLine()
                    appendLine("${index + 1}. [${vuln.severity}] ${vuln.type}")
                    appendLine("   Description: ${vuln.description}")
                    appendLine("   Details: ${vuln.details}")
                    appendLine("   Location: ${vuln.location}")
                    appendLine("   💡 Recommendation: ${vuln.recommendation}")
                }
            } else {
                appendLine("✅ No obvious vulnerabilities detected")
            }

            appendLine()
            appendLine("━".repeat(50))

            // SharedPreferences com dados sensíveis
            val sensitiveSP = storageInfo.sharedPreferences.filter { it.hasSensitiveData }
            if (sensitiveSP.isNotEmpty()) {
                appendLine()
                appendLine("🔑 SENSITIVE DATA IN SHAREDPREFERENCES")
                sensitiveSP.forEach { sp ->
                    appendLine("  • ${sp.name}")
                    appendLine("    Keys: ${sp.keys.joinToString(", ")}")
                }
            }

            // Databases
            if (storageInfo.databases.isNotEmpty()) {
                appendLine()
                appendLine("🗄️  DATABASES")
                storageInfo.databases.forEach { db ->
                    appendLine("  • ${db.name}")
                    appendLine("    Tables: ${db.tables.joinToString(", ")}")
                }
            }
        }
    }
}

// ========== Serialization Models ==========

@Serializable
private data class StorageResult(
    val databases: List<DatabaseResult>,
    val sharedPreferences: List<SharedPrefsResult>,
    val filesDir: String,
    val internalFiles: List<FileInfo>,
    val externalFiles: List<FileInfo>
)

@Serializable
private data class DatabaseResult(
    val name: String,
    val path: String,
    val tables: List<String>,
    val size: Long
)

@Serializable
private data class SharedPrefsResult(
    val name: String,
    val path: String,
    val keys: List<String>
)