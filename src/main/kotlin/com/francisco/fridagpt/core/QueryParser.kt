package com.francisco.fridagpt.core

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Resultado do parsing de uma query específica
 */
data class ParsedQuery(
    val packageName: String,
    val className: String,        // Fully qualified
    val simpleClassName: String,  // Apenas nome da classe
    val methodName: String,
    val methodSignature: String?, // Ex: "(Ljava/lang/String;)Z"
    val parameters: List<String>, // Ex: ["String", "int"]
    val returnType: String?,      // Ex: "boolean", "void"
    val action: ActionType,
    val returnValue: String?
)

/**
 * Query com múltiplos hooks
 */
data class MultiHookQuery(
    val hooks: List<ParsedQuery>,
    val globalAction: ActionType? = null,
    val globalReturnValue: String? = null
)

enum class ActionType {
    RETURN_FALSE,   // Retornar false
    RETURN_TRUE,    // Retornar true
    RETURN_NULL,    // Retornar null
    RETURN_CUSTOM,  // Retornar valor específico
    LOG_CALLS,      // Apenas logar
    MODIFY_PARAMS,  // Modificar parâmetros
    HOOK_GENERIC    // Hook genérico
}


/**
 * Resultado do parsing de uma query específica
 */
class QueryParser {

    /**
     * Tenta parsear uma query (simples ou múltipla)
     * Retorna null se não conseguir parsear
     */
    fun parse(query: String): ParsedQuery? {
        val multiHook = parseMultiHook(query)
        if (multiHook != null && multiHook.hooks.size == 1) {
            return multiHook.hooks.first()
        }
        return null
    }

    /**
     * Parseia query com suporte para múltiplos hooks
     * Separadores: AND, OR, comma (,), semicolon (;)
     */
    fun parseMultiHook(query: String): MultiHookQuery? {
        logger.debug { "Parsing multi-hook query: $query" }

        // Detectar separadores
        val separators = listOf(
            " AND " to Regex("""\s+AND\s+""", RegexOption.IGNORE_CASE),
            " OR " to Regex("""\s+OR\s+""", RegexOption.IGNORE_CASE),
            "," to Regex(""",\s*"""),
            ";" to Regex(""";\s*"""),
            "\n" to Regex("""\n+""")
        )

        // Encontrar qual separador está presente
        var segments = listOf(query)
        for ((name, regex) in separators) {
            if (regex.containsMatchIn(query)) {
                segments = query.split(regex).map { it.trim() }.filter { it.isNotEmpty() }
                logger.debug { "Split by $name: ${segments.size} segments" }
                break
            }
        }

        // Se só tem um segmento, tenta parse simples
        if (segments.size == 1) {
            val single = parseSingle(query)
            return if (single != null) {
                MultiHookQuery(hooks = listOf(single))
            } else {
                null
            }
        }

        // Parse cada segmento
        val hooks = mutableListOf<ParsedQuery>()
        var globalAction: ActionType? = null
        var globalReturnValue: String? = null

        for (segment in segments) {
            val parsed = parseSingle(segment)
            if (parsed != null) {
                hooks.add(parsed)
                // Primeira ação encontrada vira global
                if (globalAction == null) {
                    globalAction = parsed.action
                    globalReturnValue = parsed.returnValue
                }
            } else {
                logger.warn { "Failed to parse segment: $segment" }
            }
        }

        return if (hooks.isNotEmpty()) {
            MultiHookQuery(
                hooks = hooks,
                globalAction = globalAction,
                globalReturnValue = globalReturnValue
            )
        } else {
            null
        }
    }

    /**
     * Parseia um único hook
     */
    private fun parseSingle(query: String): ParsedQuery? {
        val normalized = query.lowercase().trim()

        // Tenta diferentes padrões de query
        val parsers = listOf(
            ::parseHookPattern,
            ::parseBypassPattern,
            ::parseInterceptPattern,
            ::parseReturnPattern
        )

        for (parser in parsers) {
            val result = parser(normalized, query)
            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * Padrão: hook com.example.Class.method() [action]
     * Exemplos:
     *   - hook com.example.SecurityCheck.isEmulator() return false
     *   - hook com.pentestmobile.MainActivity.checkDevice()Z
     */
    private fun parseHookPattern(normalized: String, original: String): ParsedQuery? {
        val patterns = listOf(
            // Com return value explícito
            Regex("""hook\s+([a-z][a-z0-9_.]*)\.([A-Z][a-zA-Z0-9]*)\.([a-z][a-zA-Z0-9_]*)\s*\(\s*\)\s*(.*)"""),

            // Com assinatura JNI
            Regex("""hook\s+([a-z][a-z0-9_.]+)\.([a-z_][a-z0-9_]*)\(([^)]*)\)([A-Z])""")
        )

        for (pattern in patterns) {
            val match = pattern.find(original) ?: continue

            val pack = match.groupValues[1]
            val clazz = match.groupValues[2]
            val method = match.groupValues.getOrNull(3) ?: ""
            val actionPart = match.groupValues.getOrNull(4) ?: ""

            return buildParsedQuery(pack, clazz, method, actionPart, original)
        }

        return null
    }

    /**
     * Padrão: bypass com.example.Class.method
     */
    private fun parseBypassPattern(normalized: String, original: String): ParsedQuery? {
        val pattern = Regex("""bypass\s+([a-z][a-z0-9_.]+)\.([a-z_][a-z0-9_]*)\s*(?:\(([^)]*)\))?\s*(.*)""")
        val match = pattern.find(normalized) ?: return null

        val fullClassName = match.groupValues[1]
        val methodName = match.groupValues[2]
        val params = match.groupValues.getOrNull(3) ?: ""
        val actionPart = match.groupValues.getOrNull(4) ?: "return false"

        return buildParsedQuery(fullClassName, methodName, params, actionPart, original)
    }

    /**
     * Padrão: intercept/interceptar com.example.Class.method
     */
    private fun parseInterceptPattern(normalized: String, original: String): ParsedQuery? {
        val pattern = Regex("""intercept(?:ar)?\s+([a-z][a-z0-9_.]+)\.([a-z_][a-z0-9_]*)\s*(?:\(([^)]*)\))?\s*(.*)""")
        val match = pattern.find(normalized) ?: return null

        val fullClassName = match.groupValues[1]
        val methodName = match.groupValues[2]
        val params = match.groupValues.getOrNull(3) ?: ""
        val actionPart = match.groupValues.getOrNull(4) ?: "log"

        return buildParsedQuery(fullClassName, methodName, params, actionPart, original)
    }

    /**
     * Padrão: return false/true from com.example.Class.method
     */
    private fun parseReturnPattern(normalized: String, original: String): ParsedQuery? {
        val pattern = Regex("""return\s+(false|true|null|\d+|"[^"]*")\s+from\s+([a-z][a-z0-9_.]+)\.([a-z_][a-z0-9_]*)""")
        val match = pattern.find(normalized) ?: return null

        val returnValue = match.groupValues[1]
        val fullClassName = match.groupValues[2]
        val methodName = match.groupValues[3]

        val actionPart = "return $returnValue"

        return buildParsedQuery(fullClassName, methodName, "", actionPart, original)
    }

    /**
     * Constrói ParsedQuery a partir dos componentes extraídos
     */
    private fun buildParsedQuery(
        pack: String,
        clazz: String,
        method: String,
        actionPart: String,
        originalQuery: String
    ): ParsedQuery {
        // Extrair package e classe
        val packageName = pack
        val simpleClassName = clazz

//        // Parsear parâmetros
//        val parameterList = if (params.isNotBlank()) {
//            params.split(",").map { it.trim() }.filter { it.isNotEmpty() }
//        } else {
//            emptyList()
//        }

        // Determinar ação e return value
        val (action, returnValue) = inferAction(actionPart, originalQuery)

        // Detectar return type (se disponível)
        val returnType = inferReturnType(action, returnValue)

        return ParsedQuery(
            packageName = packageName,
            className = "$packageName.$clazz",
            simpleClassName = simpleClassName,
            methodName = method,
            methodSignature = null, // Será construído depois se necessário
            parameters = emptyList(),
            returnType = returnType,
            action = action,
            returnValue = returnValue
        )
    }

    /**
     * Infere a ação baseada na parte de ação da query
     */
    private fun inferAction(actionPart: String, originalQuery: String): Pair<ActionType, String?> {
        val lower = actionPart.lowercase()

        return when {
            lower.contains("return false") || lower.contains("retornar false") ->
                ActionType.RETURN_FALSE to "false"

            lower.contains("return true") || lower.contains("retornar true") ->
                ActionType.RETURN_TRUE to "true"

            lower.contains("return null") || lower.contains("retornar null") ->
                ActionType.RETURN_NULL to "null"

            lower.contains("return") || lower.contains("retornar") -> {
                // Extrai valor customizado
                val match = Regex("""(?:return|retornar)\s+([^\s]+)""").find(lower)
                val value = match?.groupValues?.get(1)
                ActionType.RETURN_CUSTOM to value
            }

            lower.contains("log") || lower.contains("print") ||
                    lower.contains("intercept") || lower.contains("interceptar") ->
                ActionType.LOG_CALLS to null

            lower.contains("modif") || lower.contains("change") ->
                ActionType.MODIFY_PARAMS to null

            else -> ActionType.HOOK_GENERIC to null
        }
    }

    /**
     * Infere o tipo de retorno baseado na ação
     */
    private fun inferReturnType(action: ActionType, returnValue: String?): String? {
        return when (action) {
            ActionType.RETURN_FALSE, ActionType.RETURN_TRUE -> "boolean"
            ActionType.RETURN_NULL -> "Object"
            ActionType.RETURN_CUSTOM -> {
                returnValue?.let { inferTypeFromValue(it) }
            }
            else -> null
        }
    }

    /**
     * Infere tipo baseado no valor
     */
    private fun inferTypeFromValue(value: String): String {
        return when {
            value == "true" || value == "false" -> "boolean"
            value == "null" -> "Object"
            value.startsWith("\"") -> "String"
            value.toIntOrNull() != null -> "int"
            value.toDoubleOrNull() != null -> "double"
            else -> "Object"
        }
    }

    /**
     * Valida se a query parseada faz sentido
     */
    fun validate(parsed: ParsedQuery): List<String> {
        val warnings = mutableListOf<String>()

        // Validar nome da classe
        if (!parsed.className.contains('.')) {
            warnings.add("Class name should be fully qualified (e.g., com.example.MyClass)")
        }

        // Validar package
        if (parsed.packageName.isEmpty()) {
            warnings.add("Package name is empty")
        }

        // Validar nome do método
        if (parsed.methodName.isEmpty()) {
            warnings.add("Method name is empty")
        }

        // Checar convenções de nomenclatura
        if (parsed.methodName.firstOrNull()?.isUpperCase() == true) {
            warnings.add("Method name starts with uppercase (unusual in Java/Kotlin)")
        }

        return warnings
    }

    /**
     * Gera descrição legível da query parseada
     */
    fun describe(parsed: ParsedQuery): String {
        return buildString {
            appendLine("📋 Parsed Query:")
            appendLine("   Package: ${parsed.packageName}")
            appendLine("   Class: ${parsed.simpleClassName}")
            appendLine("   Method: ${parsed.methodName}(${parsed.parameters.joinToString(", ")})")
            parsed.returnType?.let { appendLine("   Returns: $it") }
            appendLine("   Action: ${parsed.action}")
            parsed.returnValue?.let { appendLine("   Value: $it") }
        }
    }

    /**
     * Exemplos de queries válidas para ajudar o usuário
     */
    fun getExamples(): List<String> {
        return listOf(
            // Hook patterns
            "hook com.example.SecurityCheck.isEmulator() return false",
            "hook com.pentestmobile.MainActivity.checkDevice()Z",
            "hook com.example.auth.LoginService.login(String,String) log calls",

            // Bypass patterns
            "bypass com.example.root.RootChecker.isRooted",
            "bypass com.example.DeviceValidator.detectEmulator() return false",

            // Intercept patterns
            "intercept com.example.api.ApiClient.makeRequest",
            "interceptar com.example.network.HttpClient.post(String,String)",

            // Return patterns
            "return false from com.example.SecurityCheck.isEmulator",
            "return null from com.example.auth.TokenManager.getToken"
        )
    }

    /**
     * Sugere correções para queries mal formadas
     */
    fun suggest(query: String): List<String> {
        val suggestions = mutableListOf<String>()

        // Se tem classe mas não método
        if (query.contains('.') && !query.contains('(')) {
            suggestions.add("Add method name: 'hook ${query.split('.').last()}.methodName()'")
        }

        // Se não tem package completo
        val parts = query.split('.')
        if (parts.size < 3) {
            suggestions.add("Use fully qualified class name: 'hook com.example.YourClass.method()'")
        }

        // Se não tem ação
        if (!query.contains("return") && !query.contains("log")) {
            suggestions.add("Specify action: add 'return false' or 'log calls'")
        }

        return suggestions
    }
}
