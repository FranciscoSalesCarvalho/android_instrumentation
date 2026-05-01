package com.francisco.fridagpt.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

private val logger = KotlinLogging.logger {}

/**
 * Gerencia conexão com Frida e execução de scripts
 */

class FridaConnector(
    val packageName: String,
    private val deviceId: String? = null, // null = USB mode
    private val port: Int,
) {
    private var isConnected = false
    private var process: Process? = null

    companion object {
        private const val FRIDA_SERVER_PORT = 27042
    }

    suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.info { "Connecting to $packageName..." }

            // Verifica se Frida está disponível
            if (!isFridaAvailable()) {
                logger.error { "Frida not found! Install: pip install frida-tools" }
                return@withContext false
            }

            if (!isAppRunning()) {
                logger.warn { "App not running, spawning..." }
                spawnApp()
            }

            isConnected = true
            logger.info { "Connected successfully!" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to connect: ${e.message}" }
            false
        }
    }

    /**
     * Executa script JavaScript via Frida
     */
    suspend fun executeCollectorScript(scriptContent: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected) {
            logger.error { "Not connected! Call connect() first" }
            return@withContext null
        }

        try {
            // Cria arquivo temporário com o script
            val scriptFile = File.createTempFile("frida_", ".js")
            scriptFile.writeText(scriptContent)

            // Executa via frida CLI
            val command = cmdForCollector(scriptFile.absolutePath)

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)

            val proc = processBuilder.start()
            val output = StringBuilder()

            // Lê output
            BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                var line: String?
                var count = 0
                while (reader.readLine().also { line = it } != null) {
                    line?.let {
                        if (count == 1 && line != "UFAM") {
                            output.appendLine(it)
                        }
                    }

                    // Condição de parada opcional
                    if (line == "UFAM")
                        count++

                    if (count == 2) break
                }
            }

//            val exitCode = proc.waitFor()
            scriptFile.delete()

            if (!isValidJson(output.toString())) {
                logger.error { "Script execution failed" }
                return@withContext null
            }

            output.toString()
        } catch (e: Exception) {
            logger.error(e) { "Script execution error: ${e.message}" }
            null
        }
    }

    suspend fun executeForResult(scriptContent: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected) {
            logger.error { "Not connected! Call connect() first" }
            return@withContext null
        }

        try {
            // Cria arquivo temporário com o script
            val scriptFile = File.createTempFile("frida_", ".js")
            scriptFile.writeText(scriptContent)

            // Executa via frida CLI
            val command = cmdForResult(scriptFile.absolutePath)

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)

            val proc = processBuilder.start()
            val output = StringBuilder()

            // Lê output com timeout
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val startTime = System.currentTimeMillis()
            val timeout = 30000L // 15 segundos
            var scriptOutputStarted = false

            while (true) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break

                    if (!scriptOutputStarted && line.trimStart().startsWith("[")) {
                        scriptOutputStarted = true
                    }

                    if (scriptOutputStarted) {
                        output.appendLine(line)
                        logger.debug { "Frida: $line" }
                    }
                }

                // Verifica se processo terminou
                if (!proc.isAlive) break

                // Verifica timeout
                if (System.currentTimeMillis() - startTime > timeout) {
                    logger.warn { "Script execution timeout, killing process" }
                    proc.destroy()
                    break
                }

                Thread.sleep(100)
            }

            reader.close()
            val exitCode = if (proc.isAlive) {
                proc.destroyForcibly()
                -1
            } else {
                proc.exitValue()
            }

            scriptFile.delete()

            if (exitCode != 0 && exitCode != -1) {
                logger.error { "Script execution failed with code $exitCode" }
                return@withContext null
            }

            output.toString()
        } catch (e: Exception) {
            logger.error(e) { "Script execution error: ${e.message}" }
            null
        }
    }

    fun isValidJson(jsonString: String): Boolean {
        return try {
            Json.parseToJsonElement(jsonString)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Desconecta do app
     */
    fun disconnect() {
        process?.destroy()
        isConnected = false
        logger.info { "Disconnected from $packageName" }
    }

    private fun isFridaAvailable(): Boolean {
        return try {
            val proc = ProcessBuilder("frida", "--version").start()
            proc.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppRunning(): Boolean {
        return try {
            val command = if (deviceId != null) {
                listOf("frida-ps", "-D", deviceId, "-a")
            } else {
                listOf("frida-ps", "-Ua")
            }

            val proc = ProcessBuilder(command).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            output.contains(packageName)
        } catch (e: Exception) {
            logger.error(e) { "Failed to check if app is running" }
            false
        }
    }

    private suspend fun spawnApp() = withContext(Dispatchers.IO) {
        try {
            val command = buildList {
                add("frida")
                if (port == FRIDA_SERVER_PORT) {
                    add("-U")
                } else {
                    add("-H")
                    add("127.0.0.1:$port")
                }
                add("-f")
                add(packageName)
            }

            ProcessBuilder(command).start()
            delay(2000)
            logger.info { "App spawned successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to spawn app" }
            throw e
        }
    }

    private fun cmdForCollector(scriptPath: String): List<String> {
        return buildList {
            add("frida")
            // Device selection
            if (port == FRIDA_SERVER_PORT && deviceId == null) {
                add("-U") // USB mode
            } else if (port != FRIDA_SERVER_PORT) {
                add("-H")
                add("127.0.0.1:$port")
            } else if (deviceId != null) {
                add("-D")
                add(deviceId)
            }
            add("-l")
            add(scriptPath)
            add("-f")
            add(packageName)
            add("-F")
            add("--runtime=v8")
        }
    }

    private fun cmdForResult(scriptPath: String): List<String> {
        return buildList {
            add("frida")
            if (port == FRIDA_SERVER_PORT && deviceId == null) {
                add("-U") // USB mode
            } else if (port != FRIDA_SERVER_PORT) {
                add("-H")
                add("127.0.0.1:$port")
            } else if (deviceId != null) {
                add("-D")
                add(deviceId)
            }
            add("-f")
            add(packageName)
            add("-l")
            add(scriptPath)
        }
    }

    /**
     * Executa script via Frida em modo attach (-n appName).
     * O -n usa o nome do app (ex: "DamnVulnerableBank"), não o package name.
     * Resolve o nome automaticamente via frida-ps.
     * Se o app não estiver rodando, inicia via adb antes do attach.
     */
    suspend fun executeScriptAttach(scriptContent: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected) {
            logger.error { "Not connected! Call connect() first" }
            return@withContext null
        }

        try {
            // Garantir que o app está rodando antes do attach
            val appName = resolveAppName()
            if (appName == null) {
                logger.info { "App not running, launching via adb..." }
                spawnApp()
                val retryName = resolveAppName()
                if (retryName == null) {
                    logger.error { "App failed to start: $packageName" }
                    return@withContext null
                }
                return@withContext executeAttach(scriptContent, retryName)
            }

            executeAttach(scriptContent, appName)
        } catch (e: Exception) {
            logger.error(e) { "Attach script execution error: ${e.message}" }
            null
        }
    }

    /**
     * Resolve o nome do app (como aparece no frida-ps) a partir do package name.
     * Ex: "com.app.damnvulnerablebank" → "DamnVulnerableBank"
     * Retorna null se o app não estiver rodando.
     */
    private fun resolveAppName(): String? {
        return try {
            val cmd = if (deviceId != null)
                listOf("frida-ps", "-D", deviceId, "-a")
            else
                listOf("frida-ps", "-U", "-a")

            val proc = ProcessBuilder(cmd).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor()

            // frida-ps -a retorna linhas como:
            //   PID  Name              Identifier
            //  1234  DamnVulnerableBank  com.app.damnvulnerablebank
            output.lines()
                .filter { it.contains(packageName) }
                .firstOrNull()
                ?.trim()
                ?.split("\\s{2,}".toRegex()) // colunas separadas por 2+ espaços
                ?.getOrNull(1)               // segunda coluna = Name
        } catch (e: Exception) {
            logger.warn { "Failed to resolve app name: ${e.message}" }
            null
        }
    }

    /**
     * Executa o attach com o nome do app já resolvido
     */
    private fun executeAttach(scriptContent: String, appName: String): String? {
        val scriptFile = File.createTempFile("frida_", ".js")
        scriptFile.writeText(scriptContent)

        val command = buildList {
            add("frida")
            if (port == FRIDA_SERVER_PORT && deviceId == null) {
                add("-U")
            } else if (port != FRIDA_SERVER_PORT) {
                add("-H")
                add("127.0.0.1:$port")
            } else if (deviceId != null) {
                add("-D")
                add(deviceId)
            }
            add("-n")
            add(appName)
            add("-l")
            add(scriptFile.absolutePath)
        }

        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)

        val proc = processBuilder.start()
        val output = StringBuilder()

        // Lê output
        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    // Recebeu payload do send() — podemos encerrar
                    if (it.contains("message")) {
                        logger.debug { "Frida: $it" }
                        output.appendLine(it)
                    }
                }

                if (output.isNotEmpty())
                    break
            }
        }

        scriptFile.delete()

        val raw = output.toString()
        val payloadRegex = """"payload"\s*:\s*"(.+?)"\s*\}""".toRegex()
        val match = payloadRegex.find(raw)

        return if (match != null) {
            match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        } else {
            raw.ifBlank { null }
        }
    }
}
