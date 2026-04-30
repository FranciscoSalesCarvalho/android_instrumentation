@file:JvmName("Main")

package com.francisco.fridagpt

import com.francisco.fridagpt.collectors.LogAnalyzer
import com.francisco.fridagpt.collectors.StorageCollector
import com.francisco.fridagpt.core.*
import com.francisco.fridagpt.llm.LLMClient
import com.francisco.fridagpt.llm.PromptBuilder
import com.francisco.fridagpt.models.ExecutionRecord
import com.francisco.fridagpt.models.MethodInfo
import com.francisco.fridagpt.utils.LogcatCapture
import com.francisco.fridagpt.utils.Spinner
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

// emulator -avd Root -writable-system
class FridaLLMTool : CliktCommand() {

    private val packageName by option(
        "-p", "--package",
        help = "Target Android package name"
    ).required()

    private val device by option(
        "-d", "--device",
        help = "Device ID (optional, uses USB by default)"
    )

    private val saveContext by option(
        "-o", "--save-context",
        help = "Save collected app context to JSON file"
    )

    private val interactive by option(
        "-i", "--interactive",
        help = "Interactive mode"
    ).flag(default = false)

    private val query by option(
        "-q", "--query",
        help = "Direct query (e.g., 'hook com.example.Class.method() return false')"
    )

    private val apiKey by option(
        "-k", "--api-key",
        help = "Claude API key (or set ANTHROPIC_API_KEY env variable)"
    )

    private val dryRun by option(
        "--dry-run",
        help = "Generate script but don't execute"
    ).flag(default = false)

    private val saveScript by option(
        "-s", "--save-script",
        help = "Save generated script to file"
    )

    private val stacktrace by option(
        "-e", "--stacktrace",
        help = "Save generated script to file"
    )

    private val analyzeLogs by option(
        "--analyze-logs",
        help = "Analyze app logs for sensitive data exposure"
    ).flag(default = false)

    private val logDuration by option(
        "--log-duration",
        help = "Duration in seconds to collect logs (default: 30)"
    ).int().default(30)

    private val port by option(
        "--port",
        help = "Port frida running"
    ).int().default(27042)

    override fun run() = runBlocking {
        printBanner()

        logger.info { "Target package: $packageName" }

        // Conectar ao app via Frida
        val connector = FridaConnector(
            packageName = packageName,
            deviceId = device,
            port = port,
        )

        if (!connector.connect()) {
            logger.error { "Failed to connect to $packageName" }
            return@runBlocking
        }

        try {
            // Obter API key
            val dotenv = dotenv()
            val key = apiKey ?: dotenv["ANTHROPIC_API_KEY"]

            if (key.isNullOrBlank() && query != null) {
                logger.error { "API key required for LLM queries. Use -k or set ANTHROPIC_API_KEY" }
                return@runBlocking
            }

            // Inicializar componentes
            val router = QueryRouter()
            val parser = QueryParser()
            val promptBuilder = PromptBuilder()
            val logcat = LogcatCapture(packageName, device)
            val executor = ScriptExecutor(
                connector = connector,
                logcatCapture = logcat,
            )
            val llmClient = if (key != null) LLMClient(key) else null
            val collector = ContextCollector(connector)

            // Inicializar RetryManager
            val retryManager = if (llmClient != null) {
                RetryManager(
                    llmClient = llmClient,
                    scriptExecutor = executor,
                    logcatCapture = logcat
                )
            } else null

            if (analyzeLogs) {
                logger.info { "Starting log analysis..." }
                val analyzer = LogAnalyzer(connector)
                val result = analyzer.analyzeLogs(logDuration)
                println(analyzer.generateReport(result))
                return@runBlocking
            }

            // Ler stacktrace se fornecido
            val stacktraceContent = stacktrace?.let { path ->
                File(path).runCatching { readText() }.getOrNull()
            }

            // Se há query direta, processar e sair
            query?.let { userQuery ->
                logger.info { "Processing direct query: $userQuery" }
                processQuery(
                    query = userQuery,
                    connector = connector,
                    collector = collector,
                    router = router,
                    parser = parser,
                    promptBuilder = promptBuilder,
                    llmClient = llmClient,
                    executor = executor,
                    retryManager = retryManager,
                    logcatCapture = logcat,
                    stacktraceContent = stacktraceContent
                )
                return@runBlocking
            }

            // Modo interativo
            if (interactive) {
                if (llmClient == null) {
                    logger.warn { "Interactive mode without API key - limited functionality" }
                }

                runInteractiveMode(
                    connector = connector,
                    router = router,
                    parser = parser,
                    collector = collector,
                    promptBuilder = promptBuilder,
                    llmClient = llmClient,
                    executor = executor,
                    retryManager = retryManager,
                    logcatCapture = logcat
                )
            }
        } finally {
            connector.disconnect()
        }
    }

    private suspend fun processQuery(
        query: String,
        connector: FridaConnector,
        collector: ContextCollector,
        router: QueryRouter,
        parser: QueryParser,
        promptBuilder: PromptBuilder,
        llmClient: LLMClient?,
        executor: ScriptExecutor,
        retryManager: RetryManager?,
        logcatCapture: LogcatCapture,
        stacktraceContent: String? = null
    ) {
        println("\n🔍 Analyzing query...")

        // Rotear query
        val queryType = router.route(query)
        println("Query type: $queryType")

        if (llmClient == null) {
            println("\n⚠️  No API key provided - showing what would be sent to LLM")
        }

        when (queryType) {
            QueryRouter.QueryType.SPECIFIC -> {
                println("\n✅ Specific query detected - Fast path!")
                handleNormalBypass(
                    query = query,
                    parser = parser,
                    promptBuilder = promptBuilder,
                    llmClient = llmClient,
                    executor = executor
                )
            }

            QueryRouter.QueryType.SEMI_SPECIFIC -> {
                println("\n⚡ Semi-specific query - Targeted search")
                handleSemiSpecificQuery(
                    query = query,
                    collector = collector,
                    promptBuilder = promptBuilder,
                    llmClient = llmClient,
                    executor = executor
                )
            }

            QueryRouter.QueryType.GENERIC -> {
                println("\n🔎 Generic query - Full context collection")

                handleGenericQuery(
                    query = query,
                    collector = collector,
                    promptBuilder = promptBuilder,
                    llmClient = llmClient,
                    executor = executor,
                    retryManager = retryManager,
                    logcatCapture = logcatCapture,
                    stacktraceContent = stacktraceContent
                )
            }
        }
    }

    /**
     * Fluxo normal para outros bypasses
     */
    private suspend fun handleNormalBypass(
        query: String,
        parser: QueryParser,
        promptBuilder: PromptBuilder,
        llmClient: LLMClient?,
        executor: ScriptExecutor
    ) {
        val multiHook = parser.parseMultiHook(query)

        if (multiHook == null || multiHook.hooks.isEmpty()) {
            println("❌ Could not parse query")
            println("Expected format: 'hook com.example.Class.method() return false'")
            println("\nFor multiple hooks use:")
            println("  hook Class1.method1() return false AND hook Class2.method2() return false")
            println("  hook Class1.method1() return false, hook Class2.method2() return false")
            return
        }

        if (multiHook.hooks.size == 1) {
            // Single hook
            val parsed = multiHook.hooks.first()
            println("\n📋 Parsed Information:")
            println("   Class: ${parsed.className}")
            println("   Method: ${parsed.methodName}")
            println("   Action: ${parsed.action}")
            parsed.returnValue?.let { println("   Return: $it") }
        } else {
            // Multiple hooks
            println("\n📋 Parsed Multi-Hook Query (${multiHook.hooks.size} hooks):")
            multiHook.hooks.forEachIndexed { index, hook ->
                println("\n   Hook ${index + 1}:")
                println("     Class: ${hook.className}")
                println("     Method: ${hook.methodName}")
                println("     Action: ${hook.action}")
                hook.returnValue?.let { println("     Return: $it") }
            }
        }

        // Gerar prompt
        val prompt = if (multiHook.hooks.size == 1) {
            promptBuilder.buildSpecificPrompt(multiHook.hooks.first())
        } else {
            promptBuilder.buildMultiHookPrompt(multiHook)
        }

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(prompt.take(500) + "...")
            return
        }

        val generated = Spinner.withSpinner("\n🤖 Generating Frida script with Claude...") {
            llmClient.generateScript(prompt, maxTokens = 8192)
        }

        if (generated == null) {
            println("❌ Failed to generate script")
            return
        }

        println("\n✅ Script generated (${generated.tokensUsed} tokens used)")

        // Salvar se solicitado
        saveScript?.let { path ->
            File("$path/test.js").writeText(generated.script)
            println("💾 Script saved to: $path")
        }

        // Validar
        val validation = executor.validateScript(generated.script)
        validation.printReport()

        if (!validation.valid) {
            println("\n⚠️  Script has validation errors - execution skipped")
            return
        }

        // Executar se não for dry-run
        if (!dryRun) {
            println("\n🚀 Executing script...")
            val result = executor.execute(generated.script)
            println(executor.formatOutput(result))
        } else {
            println("\n ℹ️  Dry-run mode - script not executed")
        }
    }

    private suspend fun handleSemiSpecificQuery(
        query: String,
        collector: ContextCollector,
        promptBuilder: PromptBuilder,
        llmClient: LLMClient?,
        executor: ScriptExecutor
    ) {
        println("\n🔍 Extracting hints from query...")
        val hints = extractQueryHints(query)
        println("Keywords: ${hints.joinToString(", ")}")

        println("\n📊 Collecting context...")
        val context = collector.collectBasicContext()

        if (context == null) {
            println("❌ Failed to collect context")
            return
        }

        val relevantClasses =
            context.classes.filter { !it.name.contains("$") && !it.name.contains("kt", ignoreCase = true) }

        println("\n✅ Found ${relevantClasses.size} relevant classes:")
        relevantClasses.take(10).forEach {
            println("   - ${it.name}")
        }

        // Detecta se precisa de múltiplos hooks
        val separators = listOf(
            " AND " to Regex("""\s+AND\s+""", RegexOption.IGNORE_CASE),
            " OR " to Regex("""\s+OR\s+""", RegexOption.IGNORE_CASE),
            "\n" to Regex("""\n+""")
        )

        var needsMultiHooks = false
        for ((_, regex) in separators) {
            if (regex.containsMatchIn(query)) {
                needsMultiHooks = true
                break
            }
        }

        if (needsMultiHooks) {
            println("\n🎯 Multi-hook scenario detected!")
            println("   Will collect ALL related methods for comprehensive hooking")
        }

        // Coletar métodos das classes relevantes
        println("\n🔄 Collecting methods from relevant classes...")
        val classesWithMethods = relevantClasses.take(if (needsMultiHooks) 10 else 5).map { classInfo ->
            val methods = collector.collectMethodsForClass(classInfo.name)

            // Filtra métodos relacionados se necessário
            val filteredMethods = if (needsMultiHooks) {
                filterRelatedMethods(methods, hints)
            } else {
                methods
            }.filter { !it.name.contains("$") }

            println("   ${classInfo.name}: ${filteredMethods.size} methods")
            classInfo.copy(methods = filteredMethods)
        }

        val totalMethods = classesWithMethods.sumOf { it.methods.size }

        if (needsMultiHooks) {
            println("\n📦 Multi-hook preparation:")
            println("   • ${classesWithMethods.size} classes")
            println("   • $totalMethods methods to hook")
            println("   • Will generate comprehensive bypass script")
        }

        // Gerar prompt
        val prompt = promptBuilder.buildContextualPrompt(
            query = query,
            relevantClasses = classesWithMethods,
            context = context,
            needsMultipleHooks = needsMultiHooks
        )

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(prompt.take(800) + "...")
            return
        }

        val generated = Spinner.withSpinner("\n🤖 Generating Frida script with Claude...") {
            llmClient.generateScript(prompt, maxTokens = 8192)
        }

        if (generated == null) {
            println("❌ Failed to generate script")
            return
        }

        println("\n✅ Script generated (${generated.tokensUsed} tokens used)")

        // Analisa quantos hooks foram gerados
        val hooksCount = countHooksInScript(generated.script)
        println("   📍 Detected $hooksCount hook(s) in generated script")

        // Salvar se solicitado
        saveScript?.let { path ->
            File("$path/test.js").writeText(generated.script)
            println("💾 Script saved to: $path")
        }

        // Validar
        val validation = executor.validateScript(generated.script)
        validation.printReport()

        if (!validation.valid) {
            println("\n⚠️  Script has validation errors - execution skipped")
            return
        }

        // Executar se não for dry-run
        if (!dryRun) {
            println("\n🚀 Executing script...")
            val result = executor.execute(generated.script, durationSeconds = 15)
            println(executor.formatOutput(result))

            // Sumário de execução para múltiplos hooks
            if (needsMultiHooks && result.success) {
                println("\n📊 Multi-hook Execution Summary:")
                println("   ✓ Expected hooks: $totalMethods")
                println("   ✓ Script executed successfully")
                println("   💡 Check output above for individual hook status")
            }
        } else {
            println("\n ℹ️  Dry-run mode - script not executed")
            if (needsMultiHooks) {
                println("   💡 This script would hook $totalMethods methods")
            }
        }
    }

    private fun extractQueryHints(query: String): List<String> {
        val words = query
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
            .filterNot { it in listOf("hook", "bypass", "method", "class", "return", "from", "with", "and", "the") }

        return words.distinct()
    }

    /**
     * Filtra métodos relacionados baseado em keywords
     */
    private fun filterRelatedMethods(
        methods: List<MethodInfo>,
        keywords: List<String>
    ): List<MethodInfo> {
        val filtered = methods.filter { method ->
            keywords.any { keyword ->
                method.name.contains(keyword, ignoreCase = true) ||
                        method.returnType.contains(keyword, ignoreCase = true)
            }
        }

        // Se filtrou muito, retorna os principais
        return if (filtered.size >= 3) filtered else methods.take(10)
    }

    /**
     * Conta quantos hooks existem no script gerado
     */
    private fun countHooksInScript(script: String): Int {
        // Conta padrões de hook: ".implementation = "
        val implementationCount = script.split(".implementation").size - 1

        // Conta padrões alternativos: ".overload"
        val overloadCount = script.split(".overload").size - 1

        return maxOf(implementationCount, overloadCount, 1)
    }

    private suspend fun handleGenericQuery(
        query: String,
        collector: ContextCollector,
        promptBuilder: PromptBuilder,
        llmClient: LLMClient?,
        executor: ScriptExecutor,
        retryManager: RetryManager?,
        logcatCapture: LogcatCapture,
        stacktraceContent: String? = null
    ) {
        println("\n📊 Collecting full context for generic query...")

        val context = collector.collectForQuery(query)

        if (context == null) {
            println("❌ Failed to collect context")
            return
        }

        // Salvar JSON se especificado
        saveContext?.let { path ->
            val jsonStr = Json { prettyPrint = true }.encodeToString(context)

            File("$path/context.json").writeText(jsonStr)
            logger.info { "Context saved to: $path" }
        }

        // Detecta se precisa de múltiplos hooks
        val separators = listOf(
            " AND " to Regex("""\s+AND\s+""", RegexOption.IGNORE_CASE),
            " OR " to Regex("""\s+OR\s+""", RegexOption.IGNORE_CASE),
            "\n" to Regex("""\n+""")
        )

        var needsMultiHooks = false
        for ((_, regex) in separators) {
            if (regex.containsMatchIn(query)) {
                needsMultiHooks = true
                break
            }
        }

        // Gerar prompt
        val prompt = promptBuilder.buildGenericPrompt(
            query = query,
            context = context,
            stacktrace = stacktraceContent,
            needsMultipleHooks = needsMultiHooks,
        )

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(prompt.take(1000) + "...")
            return
        }

        val generated = Spinner.withSpinner("\n🤖 Generating Frida script with Claude...") {
            llmClient.generateScript(prompt, maxTokens = 8192)
        }

        if (generated == null) {
            println("❌ Failed to generate script")
            return
        }

        println("\n✅ Script generated (${generated.tokensUsed} tokens used)")

        // Salvar se solicitado
        saveScript?.let { path ->
            File("$path/test.js").writeText(generated.script)
            println("💾 Script saved to: $path")
        }

        // Validar
        val validation = executor.validateScript(generated.script)
        validation.printReport()

        if (!validation.valid) {
            println("\n⚠️  Script has validation errors - execution skipped")
            return
        }

        // Executar se não for dry-run
        if (!dryRun) {
            println("\n🚀 Executing script...")
            val (result, logcat) = logcatCapture.around {
                executor.execute(generated.script, durationSeconds = 20)
            }
            println(executor.formatOutput(result))

            // Registrar execução para retry
            val serializedContext = Json { prettyPrint = true }.encodeToString(context)
            retryManager?.recordExecution(
                ExecutionRecord.fromExecution(
                    script = generated.script,
                    query = query,
                    collectedContext = serializedContext,
                    result = result,
                    logcatOutput = logcat
                )
            )
        } else {
            println("\n ℹ️  Dry-run mode - script not executed")
        }
    }

    private suspend fun runInteractiveMode(
        connector: FridaConnector,
        router: QueryRouter,
        parser: QueryParser,
        collector: ContextCollector,
        promptBuilder: PromptBuilder,
        llmClient: LLMClient?,
        executor: ScriptExecutor,
        retryManager: RetryManager?,
        logcatCapture: LogcatCapture,
    ) {
        println("\n╔════════════════════════════════════════╗")
        println("║         Interactive Mode               ║")
        println("╚════════════════════════════════════════╝")
        println()
        println("💡 Tips:")
        println("  • Specific: 'hook com.example.Class.method() return false'")
        println("  • Generic:  'bypass emulator detection'")
        println("  • Commands: 'stats', 'classes', 'frameworks', 'help', 'exit'")
        println("  • Retry:    '/retry <feedback>' to correct last script")

        if (llmClient == null) {
            println("\n⚠️  No API key - use 'setkey <key>' to enable LLM features")
        }

        val context = collector.collectFullContext()
        if (context == null) {
            logger.error { "Failed to collect context" }
            return
        }

        println()

        while (true) {
            print("fridaforge> ")
            val input = readLine()?.trim() ?: break

            if (input.isEmpty()) continue

            when {
                input.lowercase() in listOf("exit", "quit", "q") -> {
                    println("👋 Goodbye!")
                    break
                }

                input.lowercase() == "help" || input == "?" -> {
                    printHelp()
                    continue
                }

                input.lowercase() == "stats" -> {
                    collector.printStats(context)
                    continue
                }

                input.lowercase() == "classes" -> {
                    println("\n📦 Loaded Classes (first 20):")
                    context.classes.take(20).forEach {
                        println("   ${it.category.name.padEnd(10)} ${it.name}")
                    }
                    println("   ... (${context.classes.size} total)")
                    continue
                }

                input.lowercase() == "frameworks" -> {
                    println("\n🔧 Detected Frameworks:")
                    if (context.libraries.isEmpty()) {
                        println("   No frameworks detected")
                    } else {
                        context.libraries.forEach { fw ->
                            println("   ${fw.name} ${fw.version ?: ""} (${fw.type})")
                        }
                    }
                    continue
                }

                input.lowercase() == "storage" -> {
                    println("\n💾 Storage Analysis:")
                    if (context.storage != null) {
                        val report = StorageCollector(connector)
                            .generateSecurityReport(context.storage)
                        println(report)
                    } else {
                        println("   Storage info not collected. Use -c FULL")
                    }
                    continue
                }

                input.lowercase() == "logs" -> {
                    println("\n🔍 Starting log analysis...")
                    println("Duration: 30 seconds (use 'analyze-logs <seconds>' for custom duration)")
                    runBlocking {
                        val analyzer = LogAnalyzer(connector)
                        val result = analyzer.analyzeLogs(logDuration)
                        analyzer.generateReport(result)
                    }
                    continue
                }

                // Comando /retry
                input.lowercase().startsWith("/retry") -> {
                    if (retryManager == null) {
                        println("⚠️  No API key set. Retry requires LLM access.")
                        continue
                    }

                    if (!retryManager.hasRecordForRetry()) {
                        println("⚠️  No previous execution to retry. Run a query first.")
                        continue
                    }

                    // Extrair feedback do analista (tudo após "/retry ")
                    val feedback = input.removePrefix("/retry").trim().ifEmpty { null }

                    println("\n🔄 Retrying with correction...")
                    if (feedback != null) {
                        println("   Analyst feedback: \"$feedback\"")
                    } else {
                        println("   No feedback provided, LLM will analyze logs autonomously")
                    }

                    try {
                        val retryResult = retryManager.retry(feedback)

                        if (retryResult == null) {
                            println("❌ Failed to generate corrected script")
                            continue
                        }

                        println("\n✅ Corrected script generated (${retryResult.generatedScript.tokensUsed} tokens)")

                        // Mostrar resultado da execução
                        println(executor.formatOutput(retryResult.record.executionResult))

                        // Salvar se solicitado
                        saveScript?.let { path ->
                            File("$path/test.js").writeText(retryResult.generatedScript.script)
                            println("💾 Corrected script saved to: $path")
                        }

                        println("\n💡 Use '/retry <feedback>' to correct again, or submit a new query.\n")
                    } catch (e: Exception) {
                        println("❌ Retry error: ${e.message}")
                        logger.error(e) { "Retry execution error" }
                    }

                    continue
                }
            }

            // Process as query
            runBlocking {
                try {
                    if (llmClient == null) {
                        println("⚠️  No API key set. Use 'setkey <your-key>' first")
                    } else {
                        processQuery(
                            query = input,
                            connector = connector,
                            collector = collector,
                            router = router,
                            parser = parser,
                            promptBuilder = promptBuilder,
                            llmClient = llmClient,
                            executor = executor,
                            retryManager = retryManager,
                            logcatCapture = logcatCapture
                        )
                    }
                } catch (e: Exception) {
                    println("❌ Error: ${e.message}")
                    logger.error(e) { "Interactive query error" }
                }
            }
            println()
        }
    }

    private fun printHelp() {
        println(
            """
        
        📖 Help - Available Commands:
        
        Queries:
          hook <class>.<method>() return <value>  - Generate specific hook
          bypass <detection_type>                 - Generic bypass
          intercept <class>.<method>              - Intercept method calls
        
        Commands:
          stats       - Show collected context statistics
          classes     - List loaded classes
          frameworks  - Show detected frameworks
          help        - Show this help
          exit        - Exit interactive mode
          
        Correction:
         /retry                - Correct last script (LLM analyzes logs)
         /retry <feedback>     - Correct with analyst feedback
        
        Examples:
          hook com.example.SecurityCheck.isEmulator() return false
          bypass emulator detection
          intercept com.example.api.LoginService.login
          /retry hooked wrong method, should intercept validatePin
          
        """.trimIndent()
        )
    }

    private fun printBanner() {
        println(
            """
            ╔════════════════════════════════════════════════╗
            ║            FridaForge v0.2                     ║
            ║     AI-Powered Mobile Instrumentation          ║
            ║                                                ║
            ║  Smart Query Routing:                          ║
            ║    ⚡ Specific   → Direct hook generation       ║
            ║    🎯 Targeted   → Context-aware generation    ║
            ║    🔎 Generic    → Full discovery + generation ║
            ║                                                ║
            ║  🔄 /retry      → Iterative script correction  ║
            ╚════════════════════════════════════════════════╝
        """.trimIndent()
        )
    }
}

fun main(args: Array<String>) {
    FridaLLMTool().main(args)
}
