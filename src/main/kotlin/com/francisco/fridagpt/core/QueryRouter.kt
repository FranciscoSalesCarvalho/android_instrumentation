package com.francisco.fridagpt.core

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Roteia queries para o pipeline apropriado baseado na especificidade
 */
class QueryRouter {

    enum class QueryType {
        SPECIFIC,       // "hook com.example.Class.method() return false"
        SEMI_SPECIFIC,  // "bypass method isEmulator from SecurityCheck"
        GENERIC         // "bypass emulator detection"
    }

    /**
     * Determina o tipo de query baseado no conteúdo
     */
    fun route(query: String): QueryType {
        val normalized = query.lowercase().trim()

        // ESPECÍFICO: Tem FQCN (Fully Qualified Class Name) + método
        if (hasFullyQualifiedMethod(normalized)) {
            logger.debug { "Detected SPECIFIC query: has FQCN + method" }
            return QueryType.SPECIFIC
        }

        // SEMI-ESPECÍFICO: Menciona classe ou método específico
        if (hasClassOrMethodReference(normalized)) {
            logger.debug { "Detected SEMI_SPECIFIC query: has class/method reference" }
            return QueryType.SEMI_SPECIFIC
        }

        // GENÉRICO: Apenas tarefa/objetivo
        logger.debug { "Detected GENERIC query: high-level task" }
        return QueryType.GENERIC
    }

    /**
     * Verifica se query tem método totalmente qualificado
     * Exemplos:
     *   - com.example.SecurityCheck.isEmulator()
     *   - com.pentestmobile.appemulator.MainActivity.checkDevice()
     */
    private fun hasFullyQualifiedMethod(query: String): Boolean {
        // Padrão: pacote.classe.método
        // Pelo menos 3 segmentos separados por ponto + parênteses opcional
        val patterns = listOf(
            // com.example.Class.method()
            Regex("""[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}\.[a-z_][a-z0-9_]*\s*\("""),

            // com.example.Class.method (sem parênteses)
            Regex("""[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}\.[a-z_][a-z0-9_]*(?:\s|${'$'})""")
        )

        return patterns.any { it.containsMatchIn(query) }
    }

    /**
     * Verifica se menciona classe ou método específico sem FQCN completo
     * Exemplos:
     *   - "bypass isEmulator method"
     *   - "hook checkDevice from SecurityCheck"
     *   - "intercept login in AuthService"
     */
    private fun hasClassOrMethodReference(query: String): Boolean {
        // Padrões comuns que indicam especificidade parcial
        val patterns = listOf(
            // "method X" ou "método X"
            Regex("""(?:method|método|metodo)\s+[a-z_][a-z0-9_]*"""),

            // "class X" ou "classe X"
            Regex("""(?:class|classe)\s+[a-z_][a-z0-9_]*"""),

            // "from X" onde X é um nome de classe provável (CamelCase)
            Regex("""from\s+[A-Z][a-zA-Z0-9_]*"""),

            // "in X" onde X é um nome de classe
            Regex("""in\s+[A-Z][a-zA-Z0-9_]*"""),

            // "X.Y" onde Y não é um método conhecido do Android
            Regex("""[A-Z][a-zA-Z0-9_]*\.[a-z][a-zA-Z0-9_]*(?!\.)""")
        )

        return patterns.any { it.containsMatchIn(query) }
    }

    /**
     * Fornece dicas sobre como melhorar a query
     */
    fun getSuggestions(queryType: QueryType): String {
        return when (queryType) {
            QueryType.SPECIFIC -> """
                ✅ Excellent! Specific query will be fastest (~1-2s)
                
                Format detected: Fully qualified class and method
                Processing will use minimal context collection.
            """.trimIndent()

            QueryType.SEMI_SPECIFIC -> """
                ⚡ Good! Semi-specific query will be fast (~3-5s)
                
                💡 Tip: For even faster results, use fully qualified class name:
                   Instead of: "bypass isEmulator method"
                   Try: "hook com.example.SecurityCheck.isEmulator() return false"
            """.trimIndent()

            QueryType.GENERIC -> """
                🔎 Generic query will require full context (~5-10s)
                
                💡 Tip: If you know the class/method, be more specific:
                   Current: "bypass emulator detection"
                   Better: "bypass isEmulator method from SecurityCheck"
                   Best: "hook com.example.SecurityCheck.isEmulator() return false"
            """.trimIndent()
        }
    }
}
