package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.AppInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class AppInfoCollector(
    private val connector: FridaConnector
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Coleta informações básicas do app
     */
    suspend fun collect(): AppInfo? {
        logger.info { "Collecting app info..." }

        val script = getAppInfoScript()
        val rawOutput = connector.executeScript(script) ?: return null

        return try {
            // Parseia JSON retornado pelo script Frida
            val jsonStart = rawOutput.indexOf('{')
            val jsonEnd = rawOutput.lastIndexOf('}') + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.error { "No JSON found in output" }
                return null
            }

            val jsonStr = rawOutput.substring(jsonStart, jsonEnd)
            val result = json.decodeFromString<AppInfoResult>(jsonStr)

            AppInfo(
                packageName = result.packageName,
                targetSdk = result.targetSdk,
                minSdk = result.minSdk,
                isDebuggable = result.isDebuggable,
                backupActive = result.allowBackup,
                dataDir = result.dataDir,
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse app info: ${e.message}" }
            null
        }
    }

    /**
     * Script Frida para coletar informações do app
     */
    private fun getAppInfoScript(): String {
        return """
            Java.perform(function() {
                try {
                    var context = Java.use("android.app.ActivityThread").currentApplication().getApplicationContext();
                    var packageManager = context.getPackageManager();
                    
                    var packageName = context.getPackageName();
                    var minSdk = context.getApplicationInfo().minSdkVersion.value;
                    var targetSdk = context.getApplicationInfo().targetSdkVersion.value;
                    var debuggable = (context.getApplicationInfo().flags.value & 2) !== 0;
                    var allowBackup = (context.getApplicationInfo().flags.value & 0x8000) !== 0;
                    var dataDir = context.getApplicationInfo().dataDir.value; 
                    
                    var result = {
                        packageName: packageName,
                        targetSdk: targetSdk,
                        minSdk: minSdk,
                        isDebuggable: debuggable,
                        allowBackup: allowBackup,
                        dataDir: dataDir
                    };
                    
                    // Output como JSON
                    console.log("UFAM");
                    console.log(JSON.stringify(result, null, 2));
                    console.log("UFAM");
                } catch (error) {
                    console.error("Error: " + error);
                }
            });
        """.trimIndent()
    }

    @Serializable
    private data class AppInfoResult(
        val packageName: String,
        val targetSdk: Int,
        val minSdk: Int,
        val isDebuggable: Boolean,
        val allowBackup: Boolean,
        val dataDir: String
    )
}
