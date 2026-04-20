package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.NativeContext
import com.francisco.fridagpt.utils.ScriptLoader
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Coletor de Bibliotecas Nativas
 *
 * Enumera módulos nativos (.so) do processo-alvo e detecta
 * proteções implementadas em código nativo via análise de exports.
 *
 * IMPORTANTE: Deve ser executado com attach (-n) em processo já rodando.
 * Spawn (-f) causa timeout porque o app ainda não carregou as libs nativas.
 */
class NativeLibraryCollector(
    private val connector: FridaConnector
) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val SCRIPT_RESOURCE = "frida-scripts/collectors/native_libraries.js"
    }

    /**
     * Coleta contexto nativo do processo-alvo.
     * Retorna null se a coleta falhar.
     */
    suspend fun collect(): NativeContext? {
        logger.info { "Collecting native library context..." }

        return try {
            val script = ScriptLoader.load(SCRIPT_RESOURCE)
            val rawOutput = connector.executeScriptAttach(script)

            if (rawOutput.isNullOrBlank()) {
                logger.warn { "Native collector returned empty output" }
                return null
            }

            val jsonPayload = extractPayload(rawOutput)
            if (jsonPayload == null) {
                logger.error { "Failed to extract JSON payload from output" }
                return null
            }

            val context = json.decodeFromString<NativeContext>(jsonPayload)

            logger.info { "  Arch: ${context.arch}" }
            logger.info { "  Modules: ${context.summary.app} app / ${context.summary.total} total" }
            logger.info { "  Protections: ${context.protections.size}" }

            context.protections.forEach { p ->
                logger.warn { "  [${p.category}] ${p.func} @ ${p.module}" }
            }

            context
        } catch (e: Exception) {
            logger.error(e) { "Native library collection failed: ${e.message}" }
            null
        }
    }

    /**
     * Extrai o JSON do payload da saída do Frida CLI.
     *
     * Input:  message: {'type': 'send', 'payload': '{"arch":...}'} data: None
     * Output: {"arch":...}
     */
    private fun extractPayload(raw: String): String? {
        val line = raw.lines().find { it.trimStart().startsWith("message:") } ?: return null

        val marker = "'payload': '"
        val start = line.indexOf(marker)
        if (start == -1) return null

        val jsonStart = start + marker.length
        val jsonEnd = line.lastIndexOf("'}")
        if (jsonEnd == -1 || jsonEnd <= jsonStart) return null

        return line.substring(jsonStart, jsonEnd)
    }
}
