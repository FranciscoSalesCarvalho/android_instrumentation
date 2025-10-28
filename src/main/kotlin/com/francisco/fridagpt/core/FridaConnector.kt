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
    val packageName: String
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
    suspend fun executeScript(scriptContent: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected) {
            logger.error { "Not connected! Call connect() first" }
            return@withContext null
        }

        try {
            // Cria arquivo temporário com o script
            val scriptFile = File.createTempFile("frida_", ".js")
            scriptFile.writeText(scriptContent)

            // Executa via frida CLI
            val command = buildFridaCommand(scriptFile.absolutePath)
            logger.debug { "Executing: ${command.joinToString(" ")}" }

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
                            logger.debug { "Frida: $it" }
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

    suspend fun executeScript2(scriptContent: String): String? = withContext(Dispatchers.IO) {
        if (!isConnected) {
            logger.error { "Not connected! Call connect() first" }
            return@withContext null
        }

        try {
            // Cria arquivo temporário com o script
            val scriptFile = File.createTempFile("frida_", ".js")
            scriptFile.writeText(scriptContent)

            // Executa via frida CLI
            val command = buildFridaCommand2(scriptFile.absolutePath)
            logger.debug { "Executing: ${command.joinToString(" ")}" }

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)

            val proc = processBuilder.start()
            val output = StringBuilder()

            // Lê output com timeout
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val startTime = System.currentTimeMillis()
            val timeout = 15000L // 15 segundos

            while (true) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    output.appendLine(line)
                    logger.debug { "Frida: $line" }
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
            val command = listOf("frida-ps", "-Ua")

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
            val command = listOf("frida", "-U", "-f", packageName)

            val proc = ProcessBuilder(command).start()
            delay(2000)
            logger.info { "App spawned successfully" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to spawn app" }
            throw e
        }
    }

    private fun buildFridaCommand(scriptPath: String): List<String> {
        return buildList {
            add("frida")
            add("-U")
            add("-l")
            add(scriptPath)
            add("-f")
            add(packageName)
            add("-F")
            add("--runtime=v8")
        }
    }

    private fun buildFridaCommand2(scriptPath: String): List<String> {
        return buildList {
            add("frida")
            add("-U")
            add("-f")
            add(packageName)
            add("-l")
            add(scriptPath)
        }
    }
}
