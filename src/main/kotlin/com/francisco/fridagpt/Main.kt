@file:JvmName("Main")

package com.francisco.fridagpt

import com.francisco.fridagpt.collectors.LogAnalyzer
import com.francisco.fridagpt.collectors.StorageCollector
import com.francisco.fridagpt.core.*
import com.francisco.fridagpt.llm.LLMProvider
import com.francisco.fridagpt.llm.LLMProviderFactory
import com.francisco.fridagpt.llm.PromptBuilder
import com.francisco.fridagpt.models.ExecutionRecord
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

    private val llmProviderName by option(
        "--llm",
        help = "LLM provider to use: anthropic (default), openai"
    ).default("anthropic")

    private val llmModel by option(
        "--model",
        help = "Override default model for the selected LLM provider"
    )

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
            val promptBuilder = PromptBuilder()
            val logcat = LogcatCapture(packageName, device)
            val executor = ScriptExecutor(
                connector = connector,
                logcatCapture = logcat,
            )
            val llmProvider = if (key != null) {
                LLMProviderFactory.create(
                    providerName = llmProviderName,
                    apiKey = key,
                    modelOverride = llmModel
                )
            } else null

            val collector = ContextCollector(connector)

            // Inicializar RetryManager
            val retryManager = if (llmProvider != null) {
                RetryManager(
                    llmClient = llmProvider,
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
                    collector = collector,
                    router = router,
                    promptBuilder = promptBuilder,
                    llmClient = llmProvider,
                    executor = executor,
                    retryManager = retryManager,
                    logcatCapture = logcat,
                    stacktraceContent = stacktraceContent
                )
                return@runBlocking
            }

            // Modo interativo
            if (interactive) {
                if (llmProvider == null) {
                    logger.warn { "Interactive mode without API key - limited functionality" }
                }

                runInteractiveMode(
                    connector = connector,
                    router = router,
                    collector = collector,
                    promptBuilder = promptBuilder,
                    llmClient = llmProvider,
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
        collector: ContextCollector,
        router: QueryRouter,
        promptBuilder: PromptBuilder,
        llmClient: LLMProvider?,
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
        promptBuilder: PromptBuilder,
        llmClient: LLMProvider?,
        executor: ScriptExecutor
    ) {
        // Gerar prompt
        val userPrompt = promptBuilder.buildSpecificPrompt(query)

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(userPrompt.take(500) + "...")
            return
        }

        val generated = Spinner.withSpinner("\n🤖 Generating Frida script with ${llmClient.providerName}...") {
            llmClient.generateScript(PromptBuilder.buildSystemPrompt(), userPrompt)
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
        llmClient: LLMProvider?,
        executor: ScriptExecutor
    ) {
        println("\n📊 Collecting context...")
        val context = collector.collectBasicContext { message, block ->
            Spinner.withSpinner(message, block)
        }

        if (context == null) {
            println("❌ Failed to collect context")
            return
        }

        // Gerar prompt
        val userPrompt = promptBuilder.buildContextualPrompt(
            query = query,
            context = context
        )

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(userPrompt.take(800) + "...")
            return
        }

        val generated = Spinner.withSpinner("\n🤖 Generating Frida script with ${llmClient.providerName}...") {
            llmClient.generateScript(PromptBuilder.buildSystemPrompt(), userPrompt)
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
            val result = executor.execute(generated.script, durationSeconds = 15)
            println(executor.formatOutput(result))
        }
    }

    private suspend fun handleGenericQuery(
        query: String,
        collector: ContextCollector,
        promptBuilder: PromptBuilder,
        llmClient: LLMProvider?,
        executor: ScriptExecutor,
        retryManager: RetryManager?,
        logcatCapture: LogcatCapture,
        stacktraceContent: String? = null
    ) {
        println("\n📊 Collecting full context...")

        val context = collector.collectForGeneric(query) { message, block ->
            Spinner.withSpinner(message, block)
        }

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

        // Gerar prompt
        val userPrompt = promptBuilder.buildGenericPrompt(
            query = query,
            context = context,
            stacktrace = stacktraceContent
        )

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(userPrompt.take(1000) + "...")
            return
        }

        val generated = Spinner.withSpinner("\n🤖 Generating Frida script with ${llmClient.providerName}...") {
            llmClient.generateScript(PromptBuilder.buildSystemPrompt(), userPrompt)
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
        collector: ContextCollector,
        promptBuilder: PromptBuilder,
        llmClient: LLMProvider?,
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
                            collector = collector,
                            router = router,
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
