package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.FrameworkInfo
import com.francisco.fridagpt.models.FrameworkType
import com.francisco.fridagpt.utils.ScriptLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Detecta frameworks e bibliotecas utilizadas pelo app
 */
class LibraryDetector(
    private val connector: FridaConnector
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Detecta frameworks conhecidos
     */
    suspend fun detect(): List<FrameworkInfo> {
        logger.info { "Detecting libraries..." }

        val script = ScriptLoader.load("frida-scripts/collectors/libraries.js")
        val rawOutput = connector.executeCollectorScript(script) ?: return emptyList()

        return try {
            val jsonStart = rawOutput.indexOf('[')
            val jsonEnd = rawOutput.lastIndexOf(']') + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.warn { "No JSON found in framework detection output" }
                return emptyList()
            }

            val jsonStr = rawOutput.substring(jsonStart, jsonEnd)
            val detected = json.decodeFromString<List<DetectedFramework>>(jsonStr)

            detected.map { fw ->
                FrameworkInfo(
                    name = fw.name,
                    version = fw.version,
                    type = parseFrameworkType(fw.type),
                    mainClasses = fw.classes
                )
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse frameworks: ${e.message}" }
            emptyList()
        }
    }

    private fun parseFrameworkType(type: String): FrameworkType {
        return when (type.uppercase()) {
            "NETWORKING" -> FrameworkType.NETWORKING
            "SERIALIZATION" -> FrameworkType.SERIALIZATION
            "DATABASE" -> FrameworkType.DATABASE
            "UI" -> FrameworkType.UI
            "SECURITY" -> FrameworkType.SECURITY
            else -> FrameworkType.OTHER
        }
    }

    @Serializable
    private data class DetectedFramework(
        val name: String,
        val version: String,
        val type: String,
        val classes: List<String>
    )
}
