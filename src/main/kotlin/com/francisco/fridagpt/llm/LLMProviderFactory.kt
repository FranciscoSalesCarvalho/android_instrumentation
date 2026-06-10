package com.francisco.fridagpt.llm

object LLMProviderFactory {

    fun create(
        providerName: String,
        apiKey: String,
        modelOverride: String? = null
    ): LLMProvider {
        return when (providerName.lowercase().trim()) {
            "anthropic", "claude" -> {
                val model  = modelOverride ?: "claude-sonnet-4-20250514"
                AnthropicProvider(apiKey = apiKey, modelName = model)
            }
            else -> throw IllegalArgumentException(
                "Provider desconhecido: '$providerName'. " +
                        "Valores válidos: anthropic, openai"
            )
        }
    }
}