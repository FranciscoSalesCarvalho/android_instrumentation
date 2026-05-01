package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.ComponentInfo
import com.francisco.fridagpt.models.ManifestInfo
import com.francisco.fridagpt.utils.ScriptLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Coleta informações do AndroidManifest.xml
 */
class ManifestCollector(
    private val connector: FridaConnector
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Coleta informações do manifest
     */
    suspend fun collect(): ManifestInfo? {
        logger.info { "Collecting manifest info..." }

        val script = ScriptLoader.load("frida-scripts/collectors/manifest.js")
        val rawOutput = connector.executeCollectorScript(script) ?: return null

        return try {
            val jsonStart = rawOutput.indexOf('{')
            val jsonEnd = rawOutput.lastIndexOf('}') + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.warn { "No JSON found in manifest output" }
                return null
            }

            val jsonStr = rawOutput.substring(jsonStart, jsonEnd)
            val result = json.decodeFromString<ManifestResult>(jsonStr)

            ManifestInfo(
                permissions = result.permissions,
                activities = result.activities.map {
                    ComponentInfo(it.name, it.exported, it.intentFilters.map { int -> com.francisco.fridagpt.models.IntentFilter(
                        actions = int.actions,
                        categories = int.categories,
                        data = int.data
                    ) })
                },
                services = result.services.map {
                    ComponentInfo(it.name, it.exported, emptyList())
                },
                receivers = result.receivers.map {
                    ComponentInfo(it.name, it.exported, emptyList())
                },
                minSdk = result.minSdk,
                targetSdk = result.targetSdk,
                isDebuggable = result.isDebuggable
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse manifest: ${e.message}" }
            null
        }
    }

    @Serializable
    private data class ManifestResult(
        val permissions: List<String>,
        val activities: List<ComponentResult>,
        val services: List<ComponentResult>,
        val receivers: List<ComponentResult>,
        val minSdk: Int,
        val targetSdk: Int,
        val isDebuggable: Boolean
    )

    @Serializable
    private data class ComponentResult(
        val name: String,
        val exported: Boolean,
        val intentFilters: List<IntentFilter>
    )

    @Serializable
    private data class IntentFilter(
        val actions: List<String>,
        val categories: List<String>,
        val data: List<String>,
    )
}