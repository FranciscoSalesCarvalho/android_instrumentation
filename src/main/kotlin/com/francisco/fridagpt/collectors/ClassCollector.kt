package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.ClassCategory
import com.francisco.fridagpt.models.ClassInfo
import com.francisco.fridagpt.utils.ScriptLoader
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Coleta informações sobre classes carregadas
 */
class ClassCollector(
    private val connector: FridaConnector
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Coleta todas as classes (pode ser lento)
     */
    suspend fun collect(): List<ClassInfo> {
        logger.info { "Collecting all classes..." }

        val script = ScriptLoader.load("frida-scripts/collectors/all_classes.js")
        val rawOutput = connector.executeScript(script) ?: return emptyList()

        return parseClassesOutput(rawOutput)
    }

    /**
     * Coleta apenas classes do app (mais rápido)
     */
    suspend fun collectAppClassesOnly(): List<ClassInfo> {
        logger.info { "Collecting app classes only..." }

        val script = ScriptLoader.load("frida-scripts/collectors/app_classes.js")
        val rawOutput = connector.executeScript(script) ?: return emptyList()

        return parseClassesOutput(rawOutput)
    }

    private fun parseClassesOutput(output: String): List<ClassInfo> {
        return try {
            val jsonStart = output.indexOf('[')
            val jsonEnd = output.lastIndexOf(']') + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.warn { "No JSON array found in output" }
                return emptyList()
            }

            val jsonStr = output.substring(jsonStart, jsonEnd)
            val classNames = json.decodeFromString<List<String>>(jsonStr)

            // Converte nomes para ClassInfo
            classNames.map { className ->
                ClassInfo(
                    name = className,
                    packageName = extractPackageName(className),
                    category = categorizeClass(className)
                )
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse classes: ${e.message}" }
            emptyList()
        }
    }

    private fun extractPackageName(className: String): String {
        val lastDot = className.lastIndexOf('.')
        return if (lastDot > 0) className.substring(0, lastDot) else ""
    }

    private fun categorizeClass(className: String): ClassCategory {
        return when {
            className.startsWith(connector.packageName) -> ClassCategory.APP
            className.startsWith("android.") ||
                    className.startsWith("java.") ||
                    className.startsWith("javax.") ||
                    className.startsWith("kotlin.") -> ClassCategory.ANDROID

            else -> ClassCategory.LIBRARY
        }
    }
}
