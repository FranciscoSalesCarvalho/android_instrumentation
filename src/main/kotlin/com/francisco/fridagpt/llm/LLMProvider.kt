package com.francisco.fridagpt.llm

/**
 *
 * Cada implementação encapsula os detalhes de autenticação, formato de
 * requisição/resposta e endpoint de uma API específica. O restante do
 * FridaForge opera exclusivamente sobre esta interface.
 */

interface LLMProvider {

    /** Nome do provider (ex: "Claude", "ChatGPT"). Usado em mensagens ao usuário. */
    val providerName: String
    /** Identificador legível do modelo em uso (usado em logs e relatórios). */
    val modelName: String

    /**
     * Gera um script Frida a partir dos prompts fornecidos.
     *
     * @param systemPrompt  Instruções de sistema: requisitos 1–17, formato de
     *                      saída, restrições de segurança, etc.
     * @param userPrompt    Prompt do usuário: query + contexto dinâmico coletado
     *                      pelos collectors (classes, métodos, libs nativas, etc.).
     * @return              Script Frida gerado pelo modelo (texto puro, sem fences).
     * @throws LLMException Em caso de erro de autenticação, timeout ou resposta inválida.
     */
    suspend fun generateScript(systemPrompt: String, userPrompt: String): GeneratedScript?
}

/**
 * Script gerado pelo LLM, independente do provider utilizado.
 */
data class GeneratedScript(
    val script: String,       // JavaScript extraído (sem markdown fences)
    val rawResponse: String,  // Resposta bruta do modelo
    val model: String,        // Identificador do modelo que gerou o script
    val tokensUsed: Int       // Tokens de saída consumidos
)