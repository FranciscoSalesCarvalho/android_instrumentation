package com.francisco.fridagpt.core

import com.francisco.fridagpt.llm.CorrectionPromptBuilder
import com.francisco.fridagpt.llm.GeneratedScript
import com.francisco.fridagpt.llm.LLMClient
import com.francisco.fridagpt.models.ExecutionRecord
import com.francisco.fridagpt.utils.LogcatCapture
import com.francisco.fridagpt.utils.Spinner
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Orquestra o fluxo de correção iterativa de scripts Frida.
 *
 * Acionado manualmente pelo analista via comando /retry no REPL.
 * Combina os logs capturados automaticamente pelo LogcatCapture
 * com o feedback textual do analista para guiar o LLM na correção.
 *
 * Fluxo:
 * 1. Analista aciona /retry [feedback opcional]
 * 2. RetryManager recupera o último ExecutionRecord
 * 3. CorrectionPromptBuilder monta prompt com logs + feedback
 * 4. LLM gera script corrigido
 * 5. ScriptExecutor executa com LogcatCapture ao redor
 * 6. Novo ExecutionRecord é armazenado como última execução
 * 7. Output exibido ao analista (que pode acionar /retry novamente)
 */
class RetryManager(
    private val llmClient: LLMClient,
    private val scriptExecutor: ScriptExecutor,
    val logcatCapture: LogcatCapture,
    private val correctionPromptBuilder: CorrectionPromptBuilder = CorrectionPromptBuilder()
) {

    /** Último ExecutionRecord da sessão, usado pelo /retry */
    private var lastRecord: ExecutionRecord? = null

    /**
     * Registra um ExecutionRecord após qualquer execução (original ou retry).
     * Deve ser chamado pelo REPL após cada execução de script.
     */
    fun recordExecution(record: ExecutionRecord) {
        lastRecord = record
        logger.debug { "Execution recorded (attempt ${record.attemptNumber}, status: ${record.status})" }
    }

    /**
     * Executa o fluxo de correção.
     *
     * @param analystFeedback Feedback textual do analista, ou null se /retry sem argumento
     * @return Par com o novo ExecutionRecord e o GeneratedScript, ou null se falhou
     */
    suspend fun retry(analystFeedback: String? = null): RetryResult? {
        val record = lastRecord
        if (record == null) {
            logger.warn { "No previous execution to retry" }
            return null
        }

        val attemptNumber = record.attemptNumber + 1
        logger.info { "Starting retry attempt $attemptNumber for query: ${record.query}" }

        // 1. Montar prompt de correção
        val correctionPrompt = correctionPromptBuilder.buildCorrectionPrompt(
            record = record,
            analystFeedback = analystFeedback
        )

        logger.debug { "Correction prompt length: ${correctionPrompt.length} chars" }

        // 2. Enviar ao LLM
        val generatedScript = Spinner.withSpinner("\n🤖 Generating Frida script with Claude...") {
            llmClient.generateScript(correctionPrompt, maxTokens = 8192)
        }
        if (generatedScript == null) {
            logger.error { "LLM failed to generate corrected script" }
            return null
        }

        logger.info { "Corrected script generated (${generatedScript.script.length} chars, ${generatedScript.tokensUsed} tokens)" }

        // 3. Validar script
        val validation = scriptExecutor.validateScript(generatedScript.script)
        if (!validation.valid) {
            logger.warn { "Corrected script failed validation: ${validation.errors}" }
            validation.printReport()
        }

        // 4. Executar com captura de logcat
        val (executionResult, logcat) = logcatCapture.around {
            scriptExecutor.execute(generatedScript.script)
        }

        // 5. Criar novo ExecutionRecord
        val newRecord = ExecutionRecord.fromExecution(
            script = generatedScript.script,
            query = record.query,
            collectedContext = record.collectedContext,
            result = executionResult,
            logcatOutput = logcat,
            attemptNumber = attemptNumber
        )

        // 6. Atualizar último record
        recordExecution(newRecord)

        return RetryResult(
            record = newRecord,
            generatedScript = generatedScript
        )
    }

    /**
     * Verifica se há uma execução anterior disponível para retry.
     */
    fun hasRecordForRetry(): Boolean = lastRecord != null
}

/**
 * Resultado de uma tentativa de retry.
 */
data class RetryResult(
    val record: ExecutionRecord,
    val generatedScript: GeneratedScript
)