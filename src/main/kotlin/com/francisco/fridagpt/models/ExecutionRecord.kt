package com.francisco.fridagpt.models

import com.francisco.fridagpt.core.ExecutionResult

/**
 * Status da execução do script Frida.
 * Diferente do ExecutionResult.success (booleano simples),
 * este enum captura estados mais granulares para o módulo de correção.
 */
enum class ExecutionStatus {
    /** Script executou e produziu output sem exceções */
    COMPLETED,
    /** Processo da aplicação crashou durante execução */
    CRASHED,
    /** Execução excedeu o tempo limite sem resposta */
    TIMEOUT
}

/**
 * Registro completo de uma execução, agregando o resultado do ScriptExecutor
 * com o contexto necessário para o módulo de correção iterativa.
 *
 * O ScriptExecutor continua retornando ExecutionResult normalmente.
 * O ExecutionRecord é construído pelo componente que orquestra o fluxo
 * (REPL ou RetryManager), combinando o resultado com os dados de contexto.
 */
data class ExecutionRecord(
    /** Script JavaScript que foi executado */
    val script: String,

    /** Query original submetida pelo analista */
    val query: String,

    /** Contexto coletado pelos collectors (classes, métodos, libs, etc.) */
    val collectedContext: String,

    /** Resultado da execução retornado pelo ScriptExecutor */
    val executionResult: ExecutionResult,

    /** Logcat filtrado do app capturado durante a execução */
    val logcatOutput: String,

    /** Status classificado da execução */
    val status: ExecutionStatus,

    /** Número da tentativa: 1 = execução original, 2+ = retries */
    val attemptNumber: Int = 1,

    /** Timestamp da execução */
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Deriva o status a partir do ExecutionResult.
     * Heurística baseada nos padrões de output do Frida.
     */
    companion object {
        fun fromExecution(
            script: String,
            query: String,
            collectedContext: String,
            logcatOutput: String = "",
            result: ExecutionResult,
            attemptNumber: Int = 1
        ): ExecutionRecord {
            val status = classifyStatus(result)
            return ExecutionRecord(
                script = script,
                query = query,
                collectedContext = collectedContext,
                executionResult = result,
                status = status,
                attemptNumber = attemptNumber,
                logcatOutput = logcatOutput,
            )
        }

        /**
         * Classifica o status da execução com base em heurísticas
         * aplicadas ao output do Frida e ao tempo de execução.
         */
        private fun classifyStatus(result: ExecutionResult): ExecutionStatus {
            val output = result.output
            val error = result.error ?: ""

            // Crash: processo terminou abruptamente
            if (output.contains("Process crashed", ignoreCase = true) ||
                output.contains("Process terminated", ignoreCase = true) ||
                error.contains("Process crashed", ignoreCase = true) ||
                error.contains("SIGABRT", ignoreCase = true)) {
                return ExecutionStatus.CRASHED
            }

            // Timeout: sem output e execução falhou
            if (!result.success && output.isBlank() && result.executionTimeMs > 8000) {
                return ExecutionStatus.TIMEOUT
            }

            return ExecutionStatus.COMPLETED
        }
    }
}