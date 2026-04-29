package com.francisco.fridagpt.llm

import com.francisco.fridagpt.models.ExecutionRecord
import com.francisco.fridagpt.models.ExecutionStatus

/**
 * Constrói prompts de correção para o módulo de retry iterativo.
 *
 * Combina dados coletados automaticamente (script anterior, logs do Frida,
 * logcat filtrado, contexto da app) com o feedback textual do analista
 * para guiar o LLM na geração de um script corrigido.
 *
 * Reutiliza as regras de requirements do PromptBuilder existente
 * (buildRequirements() internal) para manter consistência entre
 * scripts originais e corrigidos.
 */
class CorrectionPromptBuilder {

    /**
     * Monta o prompt de correção a partir do ExecutionRecord e do feedback do analista.
     *
     * @param record Registro completo da última execução (script, logs, contexto)
     * @param analystFeedback Feedback textual do analista via /retry, ou null se sem feedback
     * @return Prompt completo para envio ao LLM
     */
    fun buildCorrectionPrompt(
        record: ExecutionRecord,
        analystFeedback: String? = null
    ): String {
        val statusDescription = describeStatus(record)
        val feedbackSection = buildFeedbackSection(analystFeedback)
        val logcatSection = buildLogcatSection(record.logcatOutput)
        val requirements = PromptBuilder.buildRequirements()

        return """
            You are an expert in Frida dynamic instrumentation for Android.
            
            A previously generated script did not produce the expected result.
            Your task is to analyze the failure and generate a CORRECTED version.
            
            ORIGINAL TASK:
            The analyst requested: "${record.query}"
            Target application package: extracted from the context below.
            
            PREVIOUS SCRIPT (ATTEMPT ${record.attemptNumber}):
            ${record.script}
            
            EXECUTION STATUS: $statusDescription
            
            EXECUTION OUTPUT (Frida):
            ${record.executionResult.output.ifBlank { "(no output captured)" }}
            
            ${if (record.executionResult.error != null) """
            EXECUTION ERROR (Frida):
            ${record.executionResult.error}
            """.trimIndent() else ""}
            
            $logcatSection
            
            $feedbackSection
            
            APPLICATION CONTEXT:
            ${record.collectedContext.ifBlank { "(no context available — script must rely on common Android patterns)" }}
            
            CORRECTION GUIDELINES:
            - Analyze the error output and the analyst's feedback to identify the root cause
            - Do NOT repeat the same approach if it already failed
            - If the previous script hooked the wrong method, find the correct target
            - If the previous script caused a crash, use a safer hooking strategy
            - If the previous script had no output, verify class/method names exist
            
            $requirements
            
            OUTPUT:
            Provide ONLY the corrected JavaScript code, no explanations before or after.
        """.trimIndent()
    }

    /**
     * Descreve o status da execução em linguagem natural para o LLM.
     */
    private fun describeStatus(record: ExecutionRecord): String {
        return when (record.status) {
            ExecutionStatus.COMPLETED ->
                "Script executed without crashing, but the analyst considers the result incorrect or incomplete."

            ExecutionStatus.CRASHED ->
                "The target application CRASHED during script execution. " +
                        "Execution time: ${record.executionResult.executionTimeMs}ms."

            ExecutionStatus.TIMEOUT ->
                "Script execution TIMED OUT with no meaningful output. " +
                        "This may indicate the script hooked a high-volume method causing the app to freeze, " +
                        "or the target method was never called during the observation window."
        }
    }

    /**
     * Constrói a seção de feedback do analista.
     * Se sem feedback, instrui o LLM a diagnosticar sozinho a partir dos logs.
     */
    private fun buildFeedbackSection(analystFeedback: String?): String {
        return if (!analystFeedback.isNullOrBlank()) {
            """
            ANALYST FEEDBACK:
            The analyst provided the following diagnosis:
            "$analystFeedback"
            
            Prioritize this feedback when determining what to fix.
            """.trimIndent()
        } else {
            """
            ANALYST FEEDBACK:
            No specific feedback provided. Analyze the execution output and error logs
            to identify and fix the issue autonomously.
            """.trimIndent()
        }
    }

    /**
     * Constrói a seção de logcat, omitindo se vazia.
     */
    private fun buildLogcatSection(logcatOutput: String): String {
        return if (logcatOutput.isNotBlank()) {
            """
            ANDROID LOGCAT (filtered):
            $logcatOutput
            """.trimIndent()
        } else {
            ""
        }
    }
}
