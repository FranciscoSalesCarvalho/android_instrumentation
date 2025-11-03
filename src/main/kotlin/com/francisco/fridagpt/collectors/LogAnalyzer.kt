package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Analisa logs do aplicativo em busca de exposição de dados sensíveis
 */
class LogAnalyzer(
    private val connector: FridaConnector
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Coleta e analisa logs do app
     */
    suspend fun analyzeLogs(durationSeconds: Int = 30): LogAnalysisResult {
        logger.info { "Starting log analysis for ${durationSeconds}s..." }

        val script = getLogInterceptionScript()
        val output = connector.executeScript(script) ?: return LogAnalysisResult(
            logs = emptyList(),
            sensitiveLogs = emptyList(),
            statistics = LogStatistics(0, 0, 0, 0)
        )

        return parseLogs(output)
    }

    /**
     * Script Frida que intercepta TODOS os métodos de logging
     */
    private fun getLogInterceptionScript(): String {
        return """
            Java.perform(function() {
                console.log("[+] Starting log interception...");
                
                var logs = [];
                
                // Hook android.util.Log (todos os níveis)
                var Log = Java.use("android.util.Log");
                
                // Verbose
                Log.v.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
                    var log = {
                        level: "VERBOSE",
                        tag: tag,
                        message: msg,
                        timestamp: Date.now()
                    };
                    logs.push(log);
                    return this.v(tag, msg);
                };
                
                // Debug
                Log.d.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
                    var log = {
                        level: "DEBUG",
                        tag: tag,
                        message: msg,
                        timestamp: Date.now()
                    };
                    logs.push(log);
                    return this.d(tag, msg);
                };
                
                // Info
                Log.i.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
                    var log = {
                        level: "INFO",
                        tag: tag,
                        message: msg,
                        timestamp: Date.now()
                    };
                    logs.push(log);
                    return this.i(tag, msg);
                };
                
                // Warning
                Log.w.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
                    var log = {
                        level: "WARNING",
                        tag: tag,
                        message: msg,
                        timestamp: Date.now()
                    };
                    logs.push(log);
                    return this.w(tag, msg);
                };
                
                // Error
                Log.e.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
                    var log = {
                        level: "ERROR",
                        tag: tag,
                        message: msg,
                        timestamp: Date.now()
                    };
                    logs.push(log);
                    return this.e(tag, msg);
                };
                
                // Hook System.out.println
                var System = Java.use("java.lang.System");
                var PrintStream = Java.use("java.io.PrintStream");
                
                PrintStream.println.overload('java.lang.String').implementation = function(msg) {
                    var log = {
                        level: "PRINTLN",
                        tag: "System.out",
                        message: msg,
                        timestamp: Date.now()
                    };
                    logs.push(log);
                    return this.println(msg);
                };
                
                console.log("[+] Log hooks installed");
                console.log("[+] Monitoring logs for 30 seconds...");
                
                // Após 30 segundos, output logs coletados
                setTimeout(function() {
                    console.log("[+] Log collection complete");
                    console.log("UFAM");
                    console.log(JSON.stringify({logs: logs}));
                    console.log("UFAM");
                }, 30000);
            });
        """.trimIndent()
    }

    /**
     * Parseia logs e identifica dados sensíveis
     */
    private fun parseLogs(output: String): LogAnalysisResult {
        try {
            val jsonStart = output.indexOf("{\"logs\"")
            val jsonEnd = output.lastIndexOf("}") + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.warn { "No log data found in output" }
                return LogAnalysisResult(
                    logs = emptyList(),
                    sensitiveLogs = emptyList(),
                    statistics = LogStatistics(0, 0, 0, 0)
                )
            }

            val jsonStr = output.substring(jsonStart, jsonEnd)
            val logData = json.decodeFromString<LogCollection>(jsonStr)

            // Analisa cada log
            val analyzedLogs = logData.logs.map { analyzeLog(it) }
            val sensitiveLogs = analyzedLogs.filter { it.isSensitive }

            // Estatísticas
            val stats = LogStatistics(
                totalLogs = analyzedLogs.size,
                sensitiveLogs = sensitiveLogs.size,
                severityHigh = sensitiveLogs.count { it.severity == SensitivityLevel.HIGH },
                severityMedium = sensitiveLogs.count { it.severity == SensitivityLevel.MEDIUM }
            )

            logger.info { "Found ${sensitiveLogs.size} sensitive logs out of ${analyzedLogs.size} total" }

            return LogAnalysisResult(
                logs = analyzedLogs,
                sensitiveLogs = sensitiveLogs,
                statistics = stats
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse logs: ${e.message}" }
            return LogAnalysisResult(
                logs = emptyList(),
                sensitiveLogs = emptyList(),
                statistics = LogStatistics(0, 0, 0, 0)
            )
        }
    }

    /**
     * Analisa um log individual em busca de dados sensíveis
     */
    private fun analyzeLog(log: LogEntry): AnalyzedLog {
        val message = log.message.lowercase()
        val detectedPatterns = mutableListOf<SensitivePattern>()
        var severity = SensitivityLevel.NONE

        // Padrões de dados sensíveis
        val patterns = mapOf(
            // Credenciais
            SensitivePattern.PASSWORD to listOf("password", "passwd", "pwd", "senha"),
            SensitivePattern.API_KEY to listOf("api_key", "apikey", "api-key", "token", "bearer"),
            SensitivePattern.SECRET to listOf("secret", "private_key", "privatekey"),

            // Dados pessoais
            SensitivePattern.EMAIL to listOf("@", "email", "e-mail"),
            SensitivePattern.PHONE to Regex("""\b\d{3}[-.]?\d{3}[-.]?\d{4}\b"""),
            SensitivePattern.CPF to Regex("""\b\d{3}\.\d{3}\.\d{3}-\d{2}\b"""),
            SensitivePattern.CREDIT_CARD to Regex("""\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b"""),

            // Dados de sessão
            SensitivePattern.SESSION_ID to listOf("session_id", "sessionid", "jsessionid"),
            SensitivePattern.AUTH_TOKEN to listOf("authorization", "auth_token", "access_token"),
            SensitivePattern.JWT to Regex("""eyJ[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+"""),

            // Dados de localização
            SensitivePattern.GPS to listOf("latitude", "longitude", "location", "gps"),
            SensitivePattern.ADDRESS to listOf("address", "endereco", "rua"),

            // Dados de dispositivo
            SensitivePattern.IMEI to Regex("""\b\d{15}\b"""),
            SensitivePattern.MAC_ADDRESS to Regex("""([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})"""),

            // URLs e caminhos
            SensitivePattern.URL to Regex("""https?://[^\s]+"""),
            SensitivePattern.FILE_PATH to listOf("/data/data/", "/sdcard/", "file://"),

            // SQL
            SensitivePattern.SQL_QUERY to listOf("select ", "insert ", "update ", "delete ", "create table")
        )

        // Verifica cada padrão
        patterns.forEach { (pattern, matchers) ->
            when (matchers) {
                is List<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val keywords = matchers as List<String>
                    if (keywords.any { message.contains(it) }) {
                        detectedPatterns.add(pattern)
                        severity = maxOf(severity, pattern.severity)
                    }
                }

                is Regex -> {
                    if (matchers.containsMatchIn(message)) {
                        detectedPatterns.add(pattern)
                        severity = maxOf(severity, pattern.severity)
                    }
                }
            }
        }

        // Detecção heurística adicional
        if (message.contains("=") && message.length > 20) {
            // Possível key=value com dados sensíveis
            if (detectedPatterns.isEmpty() && containsSuspiciousKeywords(message)) {
                detectedPatterns.add(SensitivePattern.SUSPICIOUS_DATA)
                severity = SensitivityLevel.LOW
            }
        }

        return AnalyzedLog(
            level = log.level,
            tag = log.tag,
            message = log.message,
            timestamp = log.timestamp,
            isSensitive = detectedPatterns.isNotEmpty(),
            detectedPatterns = detectedPatterns,
            severity = severity,
            recommendation = generateRecommendation(detectedPatterns)
        )
    }

    /**
     * Verifica palavras-chave suspeitas
     */
    private fun containsSuspiciousKeywords(message: String): Boolean {
        val suspiciousWords = listOf(
            "user", "usuario", "login", "auth", "credential",
            "account", "conta", "profile", "perfil"
        )
        return suspiciousWords.any { message.contains(it) }
    }

    /**
     * Gera recomendação baseada nos padrões detectados
     */
    private fun generateRecommendation(patterns: List<SensitivePattern>): String {
        if (patterns.isEmpty()) return ""

        val highSeverity = patterns.filter { it.severity == SensitivityLevel.HIGH }
        val mediumSeverity = patterns.filter { it.severity == SensitivityLevel.MEDIUM }

        return buildString {
            if (highSeverity.isNotEmpty()) {
                append("🚨 CRITICAL: Remove logs containing ")
                append(highSeverity.joinToString(", ") { it.displayName })
                append(". ")
            }
            if (mediumSeverity.isNotEmpty()) {
                append("⚠️  WARNING: Consider removing ")
                append(mediumSeverity.joinToString(", ") { it.displayName })
                append(". ")
            }
            append("Use ProGuard/R8 to strip logs in release builds.")
        }
    }

    /**
     * Gera relatório formatado
     */
    fun generateReport(result: LogAnalysisResult): String {
        return buildString {
            appendLine("╔═══════════════════════════════════════════════╗")
            appendLine("║        Log Security Analysis Report          ║")
            appendLine("╚═══════════════════════════════════════════════╝")
            appendLine()

            appendLine("📊 Statistics:")
            appendLine("   Total logs analyzed: ${result.statistics.totalLogs}")
            appendLine("   Sensitive logs found: ${result.statistics.sensitiveLogs}")
            appendLine("   └─ High severity: ${result.statistics.severityHigh}")
            appendLine("   └─ Medium severity: ${result.statistics.severityMedium}")
            appendLine()

            if (result.sensitiveLogs.isEmpty()) {
                appendLine("✅ No sensitive data found in logs!")
            } else {
                appendLine("⚠️  SENSITIVE DATA FOUND:")
                appendLine()

                // Agrupa por severidade
                val bySeverity = result.sensitiveLogs.groupBy { it.severity }

                bySeverity[SensitivityLevel.HIGH]?.let { highLogs ->
                    appendLine("🚨 HIGH SEVERITY (${highLogs.size} logs):")
                    highLogs.forEach { log ->
                        appendLine("   [${log.level}] ${log.tag}")
                        appendLine("   Message: ${log.message}${if (log.message.length > 100) "..." else ""}")
                        appendLine("   Patterns: ${log.detectedPatterns.joinToString(", ") { it.displayName }}")
                        appendLine("   ${log.recommendation}")
                        appendLine()
                    }
                }

                bySeverity[SensitivityLevel.MEDIUM]?.let { mediumLogs ->
                    appendLine("⚠️  MEDIUM SEVERITY (${mediumLogs.size} logs):")
                    mediumLogs.forEach { log ->
                        appendLine("   [${log.level}] ${log.tag}")
                        appendLine("   Message: ${log.message}...")
                        appendLine("   Patterns: ${log.detectedPatterns.joinToString(", ") { it.displayName }}")
                        appendLine()
                    }
                }

                bySeverity[SensitivityLevel.LOW]?.let { lowLogs ->
                    appendLine("ℹ️  LOW SEVERITY (${lowLogs.size} logs)")
                    appendLine()
                }
            }

            appendLine("═══════════════════════════════════════════════")
            appendLine()
            appendLine("💡 Recommendations:")
            appendLine("   1. Remove all sensitive logging in production")
            appendLine("   2. Use ProGuard/R8 to strip Log.* calls")
            appendLine("   3. Implement custom logger with filtering")
            appendLine("   4. Use BuildConfig.DEBUG checks")
            appendLine("   5. Review and sanitize error messages")
        }
    }
}

// ========== Data Models ==========

@Serializable
private data class LogCollection(
    val logs: List<LogEntry>
)

@Serializable
data class LogEntry(
    val level: String,
    val tag: String,
    val message: String,
    val timestamp: Long
)

data class AnalyzedLog(
    val level: String,
    val tag: String,
    val message: String,
    val timestamp: Long,
    val isSensitive: Boolean,
    val detectedPatterns: List<SensitivePattern>,
    val severity: SensitivityLevel,
    val recommendation: String
)

data class LogAnalysisResult(
    val logs: List<AnalyzedLog>,
    val sensitiveLogs: List<AnalyzedLog>,
    val statistics: LogStatistics
)

data class LogStatistics(
    val totalLogs: Int,
    val sensitiveLogs: Int,
    val severityHigh: Int,
    val severityMedium: Int
)

enum class SensitivityLevel {
    NONE, LOW, MEDIUM, HIGH;

    companion object {
        fun max(a: SensitivityLevel, b: SensitivityLevel): SensitivityLevel {
            return if (a.ordinal > b.ordinal) a else b
        }
    }
}

enum class SensitivePattern(val displayName: String, val severity: SensitivityLevel) {
    // Alta severidade
    PASSWORD("Password", SensitivityLevel.HIGH),
    API_KEY("API Key", SensitivityLevel.HIGH),
    SECRET("Secret Key", SensitivityLevel.HIGH),
    AUTH_TOKEN("Auth Token", SensitivityLevel.HIGH),
    JWT("JWT Token", SensitivityLevel.HIGH),
    CREDIT_CARD("Credit Card", SensitivityLevel.HIGH),

    // Média severidade
    EMAIL("Email", SensitivityLevel.MEDIUM),
    PHONE("Phone Number", SensitivityLevel.MEDIUM),
    CPF("CPF", SensitivityLevel.MEDIUM),
    SESSION_ID("Session ID", SensitivityLevel.MEDIUM),
    IMEI("IMEI", SensitivityLevel.MEDIUM),
    MAC_ADDRESS("MAC Address", SensitivityLevel.MEDIUM),

    // Baixa severidade
    GPS("GPS Coordinates", SensitivityLevel.LOW),
    ADDRESS("Address", SensitivityLevel.LOW),
    URL("URL", SensitivityLevel.LOW),
    FILE_PATH("File Path", SensitivityLevel.LOW),
    SQL_QUERY("SQL Query", SensitivityLevel.LOW),
    SUSPICIOUS_DATA("Suspicious Data", SensitivityLevel.LOW)
}

private fun maxOf(a: SensitivityLevel, b: SensitivityLevel): SensitivityLevel {
    return if (a.ordinal > b.ordinal) a else b
}