package com.francisco.fridagpt.utils

import kotlinx.coroutines.*

/**
 * Spinner animado para o terminal durante operações longas.
 *
 * Uso:
 *   val result = Spinner.withSpinner("Generating Frida script with Claude") {
 *       llmClient.generateScript(prompt)
 *   }
 */
object Spinner {

    private val frames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

    /**
     * Executa um bloco suspending enquanto exibe um spinner animado no terminal.
     * A mensagem é impressa uma vez, e o spinner anima numa linha dedicada abaixo.
     *
     * @param message Mensagem exibida antes do spinner
     * @param block Operação a executar
     * @return Resultado do bloco
     */
    suspend fun <T> withSpinner(message: String, block: suspend () -> T): T {
        var running = true

        System.out.println(message)
        System.out.flush()

        val spinnerJob = CoroutineScope(Dispatchers.IO).launch {
            var i = 0
            while (running) {
                System.out.print("\r${frames[i % frames.size]}")
                System.out.flush()
                delay(80)
                i++
            }
        }

        return try {
            val result = block()
            running = false
            spinnerJob.join()
            System.out.print("\r${" ".repeat(message.length + 4)}\r")
            System.out.flush()
            result
        } catch (e: Exception) {
            running = false
            spinnerJob.join()
            System.out.print("\r❌\n")
            System.out.flush()
            throw e
        }
    }
}
