package com.francisco.fridagpt.core

import com.francisco.fridagpt.utils.LogcatCapture
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Resultado da execução de um script
 */
data class ExecutionResult(
    val success: Boolean,
    val output: String,
    val error: String? = null,
    val logcatOutput: String = "",
    val executionTimeMs: Long
)

/**
 * Executa e gerencia scripts Frida gerados.
 *
 * @param connector Conector Frida para execução de scripts
 * @param logcatCapture Captura de logcat (opcional). Quando presente,
 *        o executor limpa o logcat antes da execução e captura os logs
 *        filtrados automaticamente após a execução.
 */
class ScriptExecutor(
    private val connector: FridaConnector,
    private val logcatCapture: LogcatCapture? = null
) {

    /**
     * Valida sintaxe básica do script JavaScript
     */
    fun validateScript(script: String): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Verificações básicas
        if (!script.contains("Java.perform")) {
            errors.add("Script must contain Java.perform() wrapper")
        }

        if (!script.contains("Java.use")) {
            warnings.add("Script doesn't use Java.use() - may not hook anything")
        }

        // Verifica balanço de chaves
        val openBraces = script.count { it == '{' }
        val closeBraces = script.count { it == '}' }
        if (openBraces != closeBraces) {
            errors.add("Unbalanced braces: $openBraces open, $closeBraces close")
        }

        // Verifica balanço de parênteses
        val openParens = script.count { it == '(' }
        val closeParens = script.count { it == ')' }
        if (openParens != closeParens) {
            errors.add("Unbalanced parentheses: $openParens open, $closeParens close")
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }

    /**
     * Executa script e monitora output.
     * Se LogcatCapture estiver configurado, captura logcat automaticamente.
     */
    suspend fun execute(script: String, durationSeconds: Int = 10): ExecutionResult {
        logger.info { "Executing Frida script (duration: ${durationSeconds}s)..." }

        if (logcatCapture != null) {
            val (result, logcat) = logcatCapture.around {
                executeInternal(script)
            }
            return result.copy(logcatOutput = logcat)
        }

        return executeInternal(script)
    }

    /**
     * Executa script e monitora output
     */
    private suspend fun executeInternal(script: String): ExecutionResult {

        val startTime = System.currentTimeMillis()

        try {
            // Salva script em arquivo temporário
            val scriptFile = File.createTempFile("frida_generated_", ".js")
            scriptFile.writeText(script)

            // Executa script
            val output = connector.executeScript2(script)

            val executionTime = System.currentTimeMillis() - startTime

            if (output == null) {
                return ExecutionResult(
                    success = false,
                    output = "",
                    error = "Script execution failed - no output received",
                    executionTimeMs = executionTime
                )
            }

            // Analisa output para detectar erros
            val hasError = output.contains("Error:", ignoreCase = true) ||
                    output.contains("Exception", ignoreCase = true) ||
                    output.contains("Failed", ignoreCase = true)

            logger.info { "Script executed in ${executionTime}ms" }

            return ExecutionResult(
                success = !hasError,
                output = output,
                error = if (hasError) extractError(output) else null,
                executionTimeMs = executionTime
            )

        } catch (e: Exception) {
            logger.error(e) { "Script execution exception: ${e.message}" }

            return ExecutionResult(
                success = false,
                output = "",
                error = e.message,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Extrai mensagem de erro do output
     */
    private fun extractError(output: String): String {
        val errorLines = output.lines().filter { line ->
            line.contains("Error:", ignoreCase = true) ||
                    line.contains("Exception", ignoreCase = true) ||
                    line.contains("Failed", ignoreCase = true)
        }

        return errorLines.joinToString("\n").take(500)
    }

    /**
     * Formata output para exibição
     */
    fun formatOutput(result: ExecutionResult): String {
        return buildString {
            appendLine("╔════════════════════════════════════════╗")
            appendLine("║         Execution Result               ║")
            appendLine("╚════════════════════════════════════════╝")
            appendLine()

            appendLine("Status: ${if (result.success) "✅ SUCCESS" else "❌ FAILED"}")
            appendLine("Execution time: ${result.executionTimeMs}ms")
            appendLine()

            if (result.error != null) {
                appendLine("❌ Error:")
                appendLine(result.error)
                appendLine()
            }

            appendLine("📄 Output:")
            appendLine(result.output.lines().take(50).joinToString("\n"))

            if (result.output.lines().size > 50) {
                appendLine("... (output truncated)")
            }
        }
    }
}

/**
 * Resultado da validação
 */
data class ValidationResult(
    val valid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
) {
    fun printReport() {
        if (valid) {
            println("✅ Script validation passed")
        } else {
            println("❌ Script validation failed")
        }

        if (errors.isNotEmpty()) {
            println("\nErrors:")
            errors.forEach { println("  ❌ $it") }
        }

        if (warnings.isNotEmpty()) {
            println("\nWarnings:")
            warnings.forEach { println("  ⚠️  $it") }
        }
    }
}