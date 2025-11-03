package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.DatabaseInfo
import com.francisco.fridagpt.models.FileInfo
import com.francisco.fridagpt.models.Severity
import com.francisco.fridagpt.models.SharedPreferencesInfo
import com.francisco.fridagpt.models.StorageInfo
import com.francisco.fridagpt.models.StorageVulnerability
import com.francisco.fridagpt.models.VulnerabilityType
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

        val script = getStorageScript()
        val rawOutput = connector.executeScript(script) ?: return null

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
     * Script Frida para coletar informações de armazenamento
     */
    private fun getStorageScript(): String {
        return """
            Java.perform(function() {
                console.log("[+] Collecting storage information...");
                
                try {
                    var context = Java.use("android.app.ActivityThread").currentApplication().getApplicationContext();
                    var File = Java.use("java.io.File");
                    
                    var filesDir = context.getFilesDir().getAbsolutePath();
                    var dataDir = context.getApplicationInfo().dataDir.value;
                    
                    // ========== DATABASES ==========
                    console.log("[+] Scanning databases...");
                    var databases = [];
                    var dbDir = File.${'$'}new(dataDir + "/databases");
                    
                    if (dbDir.exists()) {
                        var dbFiles = dbDir.listFiles();
                        if (dbFiles) {
                            for (var i = 0; i < dbFiles.length; i++) {
                                var dbFile = dbFiles[i];
                                if (dbFile.isFile() && !dbFile.getName().endsWith("-journal")) {
                                    var dbPath = dbFile.getAbsolutePath();
                                    var tables = [];
                                    
                                    // Tentar ler tabelas do SQLite
                                    try {
                                        var SQLiteDatabase = Java.use("android.database.sqlite.SQLiteDatabase");
                                        var db = SQLiteDatabase.openDatabase(
                                            dbPath, 
                                            null, 
                                            SQLiteDatabase.OPEN_READONLY.value
                                        );
                                        
                                        var cursor = db.rawQuery(
                                            "SELECT name FROM sqlite_master WHERE type='table'", 
                                            null
                                        );
                                        
                                        while (cursor.moveToNext()) {
                                            tables.push(cursor.getString(0));
                                        }
                                        cursor.close();
                                        db.close();
                                    } catch(e) {
                                        console.log("[-] Could not read tables from " + dbFile.getName());
                                    }
                                    
                                    databases.push({
                                        name: dbFile.getName(),
                                        path: dbPath,
                                        tables: tables,
                                        size: dbFile.length()
                                    });
                                }
                            }
                        }
                    }
                    
                    // ========== SHARED PREFERENCES ==========
                    console.log("[+] Scanning SharedPreferences...");
                    var sharedPrefs = [];
                    var prefsDir = File.${'$'}new(dataDir + "/shared_prefs");
                    
                    if (prefsDir.exists()) {
                        var prefsFiles = prefsDir.listFiles();
                        if (prefsFiles) {
                            for (var i = 0; i < prefsFiles.length; i++) {
                                var prefsFile = prefsFiles[i];
                                if (prefsFile.isFile() && prefsFile.getName().endsWith(".xml")) {
                                    var prefsName = prefsFile.getName().replace(".xml", "");
                                    var keys = [];
                                    
                                    // Ler keys do SharedPreferences
                                    try {
                                        var prefs = context.getSharedPreferences(prefsName, 0);
                                        var allEntries = prefs.getAll();
                                        var keySet = allEntries.keySet();
                                        var iterator = keySet.iterator();
                                        
                                        while (iterator.hasNext()) {
                                            var key = iterator.next();
                                            keys.push(key.toString());
                                        }
                                    } catch(e) {
                                        console.log("[-] Could not read keys from " + prefsName);
                                    }
                                    
                                    sharedPrefs.push({
                                        name: prefsName,
                                        path: prefsFile.getAbsolutePath(),
                                        keys: keys
                                    });
                                }
                            }
                        }
                    }
                    
                    // ========== INTERNAL FILES ==========
                    console.log("[+] Scanning internal files...");
                    var internalFiles = [];
                    var filesDirObj = File.${'$'}new(filesDir);
                    
                    if (filesDirObj.exists()) {
                        var files = filesDirObj.listFiles();
                        if (files) {
                            for (var i = 0; i < files.length; i++) {
                                var file = files[i];
                                internalFiles.push({
                                    name: file.getName(),
                                    path: file.getAbsolutePath(),
                                    size: file.length(),
                                    isDirectory: file.isDirectory()
                                });
                            }
                        }
                    }
                    
                    // ========== EXTERNAL STORAGE ==========
                    console.log("[+] Checking external storage...");
                    var externalFiles = [];
                    
                    try {
                        var externalDir = context.getExternalFilesDir(null);
                        if (externalDir && externalDir.exists()) {
                            var extFiles = externalDir.listFiles();
                            if (extFiles) {
                                for (var i = 0; i < extFiles.length; i++) {
                                    var file = extFiles[i];
                                    externalFiles.push({
                                        name: file.getName(),
                                        path: file.getAbsolutePath(),
                                        size: file.length()
                                    });
                                }
                            }
                        }
                    } catch(e) {
                        console.log("[-] No external storage access");
                    }
                    
                    // ========== RESULT ==========
                    var result = {
                        databases: databases,
                        sharedPreferences: sharedPrefs,
                        filesDir: filesDir,
                        internalFiles: internalFiles,
                        externalFiles: externalFiles
                    };
                    
                    console.log("[+] Storage scan completed");
                    console.log("UFAM");
                    console.log(JSON.stringify(result, null, 2));
                    console.log("UFAM");
                    
                } catch(e) {
                    console.error("[-] Error collecting storage: " + e.toString());
                    console.error("Stack: " + e.stack);
                }
            });
        """.trimIndent()
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