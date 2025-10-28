package com.francisco.fridagpt.llm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Cliente para Anthropic Claude API
 */
class LLMClient(
    private val apiKey: String,
    private val model: String = "claude-sonnet-4-20250514"
) {
    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000 // 2 minutes for Claude API
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
            })
        }
    }

    private val baseUrl = "https://api.anthropic.com/v1/messages"

    /**
     * Gera script Frida usando Claude
     */
    suspend fun generateScript(prompt: String, maxTokens: Int = 4096): GeneratedScript? {
        return try {
            logger.info { "Sending request to Claude API..." }
            logger.debug { "Prompt length: ${prompt.length} chars" }

            val request = ClaudeRequest(
                model = model,
                maxTokens = maxTokens,
                messages = listOf(
                    Message(
                        role = "user",
                        content = prompt
                    )
                )
            )

            val response = client.post(baseUrl) {
                header("x-api-key", apiKey)
                header("anthropic-version", "2023-06-01")
                contentType(ContentType.Application.Json)
                setBody(request)
            }

            if (response.status != HttpStatusCode.OK) {
                logger.error { "Claude API error: ${response.status}" }
                return null
            }

            val claudeResponse = response.body<ClaudeResponse>()
            val scriptText = claudeResponse.content.firstOrNull()?.text ?: ""

            logger.info { "Script generated successfully (${scriptText.length} chars)" }

            GeneratedScript(
                script = extractJavaScript(scriptText),
                rawResponse = scriptText,
                model = model,
                tokensUsed = claudeResponse.usage.outputTokens
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate script: ${e.message}" }
            null
        }
    }

    /**
     * Extrai apenas o código JavaScript da resposta
     */
    private fun extractJavaScript(response: String): String {
        // Tenta encontrar bloco de código JavaScript
        val jsBlockRegex = Regex("""```(?:javascript|js)?\n(.*?)\n```""", RegexOption.DOT_MATCHES_ALL)
        val match = jsBlockRegex.find(response)

        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // Se não tem markdown, assume que tudo é código
            response.trim()
        }
    }

    fun close() {
        client.close()
    }
}

/**
 * Script gerado pelo LLM
 */
data class GeneratedScript(
    val script: String,
    val rawResponse: String,
    val model: String,
    val tokensUsed: Int
)

@Serializable
private data class ClaudeRequest(
    val model: String,
    @SerialName("max_tokens")
    val maxTokens: Int,
    val messages: List<Message>
)

@Serializable
private data class Message(
    val role: String,
    val content: String
)

@Serializable
private data class ClaudeResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<ContentBlock>,
    val model: String,
    @SerialName("stop_reason")
    val stopReason: String? = null,
    val usage: Usage
)

@Serializable
private data class ContentBlock(
    val type: String,
    val text: String
)

@Serializable
private data class Usage(
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int
)
