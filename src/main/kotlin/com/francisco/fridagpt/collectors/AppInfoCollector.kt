package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.AppInfo
import com.francisco.fridagpt.utils.ScriptLoader
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

        val script = ScriptLoader.load("frida-scripts/collectors/app_info.js")
        val rawOutput = connector.executeCollectorScript(script) ?: return null

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
