package com.francisco.fridagpt.utils

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Captura e filtra logs do Android logcat durante a execução de scripts Frida.
 *
 * Responsabilidades:
 * - Limpar logcat antes da execução (evitar logs residuais)
 * - Capturar logcat filtrado pelo PID do app alvo após a execução
 * - Filtrar linhas relevantes para correção (exceções, erros, crashes)
 * - Truncar output para não estourar o limite de tokens do prompt
 *
 * Uso:
 *   val logcat = LogcatCapture(packageName, deviceId)
 *   val (result, logs) = logcat.around {
 *       scriptExecutor.execute(script)
 *   }
 *   val record = ExecutionRecord.fromExecution(..., logcatOutput = logs)
 */
class LogcatCapture(
    private val packageName: String,
    private val deviceId: String? = null,
    private val maxChars: Int = 2000
) {

    /**
     * Encapsula a execução de um bloco com captura automática de logcat.
     * Limpa o buffer antes, executa o bloco, e captura os logs depois.
     *
     * @param block Bloco a executar (tipicamente scriptExecutor.execute(script))
     * @return Par com o resultado do bloco e os logs filtrados
     */
    suspend fun <T> around(block: suspend () -> T): Pair<T, String> {
        clear()
        val result = block()
        val logs = capture()
        return Pair(result, logs)
    }

    /**
     * Limpa o buffer do logcat antes de executar o script
     * para garantir que só capturamos logs da execução atual.
     */
    private fun clear() {
        try {
            val command = buildAdbCommand("logcat", "-c")
            val proc = ProcessBuilder(command).start()
            proc.waitFor()
            logger.debug { "Logcat buffer cleared" }
        } catch (e: Exception) {
            logger.warn { "Failed to clear logcat: ${e.message}" }
        }
    }

    /**
     * Captura logcat filtrado pelo PID do app e por relevância.
     *
     * @return Logs filtrados e truncados, prontos para inclusão no prompt
     */
    private fun capture(): String {
        try {
            val pid = resolveAppPid()

            val rawLogs = if (pid != null) {
                captureByPid(pid)
            } else {
                logger.warn { "Could not resolve PID for $packageName, capturing by tag" }
                captureByTag()
            }

            val filtered = filterRelevantLines(rawLogs)
            return truncate(filtered)
        } catch (e: Exception) {
            logger.error { "Failed to capture logcat: ${e.message}" }
            return ""
        }
    }

    /**
     * Resolve o PID do app alvo via adb shell pidof.
     */
    private fun resolveAppPid(): Int? {
        return try {
            val command = buildAdbCommand("shell", "pidof", packageName)
            val proc = ProcessBuilder(command).start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()

            // pidof pode retornar múltiplos PIDs separados por espaço
            output.split(" ").firstOrNull()?.toIntOrNull()
        } catch (e: Exception) {
            logger.debug { "Failed to resolve PID: ${e.message}" }
            null
        }
    }

    /**
     * Captura logcat filtrado por PID do app.
     * Flag -d faz dump e sai imediatamente (não fica em modo streaming).
     */
    private fun captureByPid(pid: Int): String {
        val command = buildAdbCommand("logcat", "-d", "--pid=$pid")
        val proc = ProcessBuilder(command).start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return output
    }

    /**
     * Fallback: captura logcat filtrando por tags comuns de erro.
     * Usado quando não conseguimos resolver o PID (ex: app crashou).
     */
    private fun captureByTag(): String {
        val command = buildAdbCommand("logcat", "-d", "*:E")
        val proc = ProcessBuilder(command).start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return output
    }

    /**
     * Tags do sistema Android que geram ruído e não são relevantes
     * para diagnóstico de erros em scripts Frida.
     */
    private val ignoredTags = setOf(
        "ashmem", "HWUI", "OpenGLRenderer", "EGL_emulation",
        "Gralloc", "SurfaceFlinger", "InputDispatcher", "InputReader",
        "AudioFlinger", "AudioTrack", "MediaPlayer", "MediaCodec",
        "NetworkSecurityConfig", "ConfigStore", "libEGL", "libGLES",
        "chatty", "ServiceManager", "Zygote", "ActivityThread",
        "RenderThread", "GraphicsEnvironment", "Choreographer",
        "InsetsController", "ViewRootImpl", "WindowManager",
        "ConnectivityManager", "WifiManager", "BluetoothAdapter",
        "SensorManager", "PowerManager", "BatteryManager"
    )

    /**
     * Filtra linhas relevantes para diagnóstico de erros.
     * Mantém: exceções, stack traces, erros, crashes, e output do Frida.
     * Descarta: logs de info/debug genéricos e tags de sistema conhecidas.
     */
    private fun filterRelevantLines(rawLogs: String): String {
        val lines = rawLogs.lines()

        val relevantLines = mutableListOf<String>()
        var inStackTrace = false

        for (line in lines) {
            // Ignorar tags de sistema conhecidas
            if (isSystemNoise(line)) continue

            // Stack traces: manter bloco completo uma vez detectado
            if (inStackTrace) {
                if (line.contains("\tat ") || line.contains("Caused by:")) {
                    relevantLines.add(line)
                    continue
                } else {
                    inStackTrace = false
                }
            }

            // Início de exceção/stack trace
            if (line.contains("Exception", ignoreCase = true) ||
                line.contains("FATAL", ignoreCase = true) ||
                line.contains("Caused by:", ignoreCase = true)
            ) {
                relevantLines.add(line)
                inStackTrace = true
                continue
            }

            // Erros e crashes
            if (line.contains(" E ", ignoreCase = false) ||
                line.contains("Error", ignoreCase = true) ||
                line.contains("crash", ignoreCase = true) ||
                line.contains("SIGABRT", ignoreCase = true) ||
                line.contains("died", ignoreCase = true)
            ) {
                relevantLines.add(line)
                continue
            }

            // Output do Frida no logcat
            if (line.contains("Frida", ignoreCase = true) ||
                line.contains("frida-agent", ignoreCase = true)
            ) {
                relevantLines.add(line)
                continue
            }
        }

        return relevantLines.joinToString("\n")
    }

    /**
     * Verifica se a linha pertence a uma tag de sistema conhecida (ruído).
     */
    private fun isSystemNoise(line: String): Boolean {
        return ignoredTags.any { tag -> line.contains(tag, ignoreCase = true) }
    }

    /**
     * Trunca output para respeitar o limite de caracteres.
     * Mantém as primeiras e últimas linhas para preservar contexto.
     */
    private fun truncate(logs: String): String {
        if (logs.length <= maxChars) return logs

        val half = maxChars / 2
        val start = logs.substring(0, half)
        val end = logs.substring(logs.length - half)
        return "$start\n... [truncated] ...\n$end"
    }

    /**
     * Constrói comando adb com device selector opcional.
     */
    private fun buildAdbCommand(vararg args: String): List<String> {
        return buildList {
            add("adb")
            if (deviceId != null) {
                add("-s")
                add(deviceId)
            }
            addAll(args)
        }
    }
}
