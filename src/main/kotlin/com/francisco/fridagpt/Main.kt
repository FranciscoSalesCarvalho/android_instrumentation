package com.francisco.fridagpt

import com.francisco.fridagpt.core.*
import com.francisco.fridagpt.llm.LLMClient
import com.francisco.fridagpt.llm.PromptBuilder
import com.francisco.fridagpt.models.AppContext
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

class FridaLLMTool : CliktCommand() {

    private val packageName by option(
        "-p", "--package",
        help = "Target Android package name"
    ).required()

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

    override fun run() = runBlocking {
        printBanner()

        logger.info { "Target package: $packageName" }

        val connector = FridaConnector(packageName)

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
            val collector = ContextCollector(connector)
            val router = QueryRouter()
            val parser = QueryParser()
            val promptBuilder = PromptBuilder()
            val executor = ScriptExecutor(connector)
            val llmClient = if (key != null) LLMClient(key) else null

            // Se há query direta, processar e sair
            query?.let { userQuery ->
                logger.info { "Processing direct query: $userQuery" }
                processQuery(
                    userQuery, connector, collector, router, parser,
                    promptBuilder, llmClient, executor
                )
                return@runBlocking
            }

            val context = collector.collectFullContext()

            if (context == null) {
                logger.error { "Failed to collect context" }
                return@runBlocking
            }

            // Mostrar estatísticas
            collector.printStats(context)
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
        executor: ScriptExecutor
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
                handleSpecificQuery(query, parser, promptBuilder, llmClient, executor)
            }

            QueryRouter.QueryType.SEMI_SPECIFIC -> {
                println("\n⚡ Semi-specific query - Targeted search")
//                handleSemiSpecificQuery(query, collector, parser, promptBuilder, llmClient, executor)
            }

            QueryRouter.QueryType.GENERIC -> {
                println("\n🔎 Generic query - Full context collection")
//                handleGenericQuery(query, collector, promptBuilder, llmClient, executor)
            }
        }
    }

    private suspend fun handleSpecificQuery(
        query: String,
        parser: QueryParser,
        promptBuilder: PromptBuilder,
        llmClient: LLMClient?,
        executor: ScriptExecutor
    ) {
        val parsed = parser.parse(query)

        if (parsed == null) {
            println("❌ Could not parse specific query")
            println("Expected format: 'hook com.example.Class.method() return false'")
            return
        }

        println("\n📋 Parsed Information:")
        println("   Class: ${parsed.className}")
        println("   Method: ${parsed.methodName}")
        println("   Action: ${parsed.action}")
        parsed.returnValue?.let { println("   Return: $it") }

        // Gerar prompt
        val prompt = promptBuilder.buildSpecificPrompt(parsed)

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(prompt.take(500) + "...")
            return
        }

        println("\n🤖 Generating Frida script with Claude...")
        val generated = llmClient.generateScript(prompt)

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

        // Mostrar script
        println("\n📜 Generated Script:")
        println("━".repeat(50))
        println(generated.script)
        println("━".repeat(50))

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

//    private suspend fun handleSemiSpecificQuery(
//        query: String,
//        collector: ContextCollector,
//        parser: QueryParser,
//        promptBuilder: PromptBuilder,
//        llmClient: LLMClient?,
//        executor: ScriptExecutor
//    ) {
//        println("\n🔍 Extracting hints from query...")
//        val hints = extractQueryHints(query)
//        println("Keywords: ${hints.joinToString(", ")}")
//
//        println("\n📊 Collecting context...")
//        val context = collector.collectFullContext()
//
//        if (context == null) {
//            println("❌ Failed to collect context")
//            return
//        }
//
//        // Filtrar classes relevantes
//        val relevantClasses = context.classes.filter { classInfo ->
//            hints.any { hint ->
//                classInfo.name.contains(hint, ignoreCase = true)
//            }
//        }
//
//        println("\n✅ Found ${relevantClasses.size} relevant classes:")
//        relevantClasses.take(10).forEach {
//            println("   - ${it.name}")
//        }
//
//        // Coletar métodos das classes relevantes
//        println("\n🔄 Collecting methods from relevant classes...")
//        val classesWithMethods = relevantClasses.take(5).map { classInfo ->
//            val methods = collector.colle(classInfo.name)
//            println("   ${classInfo.name}: ${methods.size} methods")
//            classInfo.copy(methods = methods)
//        }
//
//        // Gerar prompt
//        val prompt = promptBuilder.buildContextualPrompt(query, classesWithMethods, context)
//
//        if (llmClient == null) {
//            println("\n📝 Prompt that would be sent:")
//            println("━".repeat(50))
//            println(prompt.take(800) + "...")
//            return
//        }
//
//        println("\n🤖 Generating Frida script with Claude...")
//        val generated = llmClient.generateScript(prompt, maxTokens = 8192)
//
//        if (generated == null) {
//            println("❌ Failed to generate script")
//            return
//        }
//
//        println("\n✅ Script generated (${generated.tokensUsed} tokens used)")
//
//        // Salvar se solicitado
//        saveScript?.let { path ->
//            File(path).writeText(generated.script)
//            println("💾 Script saved to: $path")
//        }
//
//        // Mostrar script
//        println("\n📜 Generated Script:")
//        println("━".repeat(50))
//        println(generated.script)
//        println("━".repeat(50))
//
//        // Validar
//        val validation = executor.validateScript(generated.script)
//        validation.printReport()
//
//        if (!validation.valid) {
//            println("\n⚠️  Script has validation errors - execution skipped")
//            return
//        }
//
//        // Executar se não for dry-run
//        if (!dryRun) {
//            println("\n🚀 Executing script...")
//            val result = executor.execute(generated.script, durationSeconds = 15)
//            println(executor.formatOutput(result))
//        } else {
//            println("\n ℹ️  Dry-run mode - script not executed")
//        }
//    }

//    private suspend fun handleGenericQuery(
//        query: String,
//        collector: ContextCollector,
//        promptBuilder: PromptBuilder,
//        llmClient: LLMClient?,
//        executor: ScriptExecutor
//    ) {
//        println("\n📊 Collecting full context for generic query...")
//
//        val context = collector.collectForQuery(query)
//
//        if (context == null) {
//            println("❌ Failed to collect context")
//            return
//        }
//
//        collector.printStats(context)
//
//        // Gerar prompt
//        val prompt = promptBuilder.buildGenericPrompt(query, context)
//
//        if (llmClient == null) {
//            println("\n📝 Prompt that would be sent:")
//            println("━".repeat(50))
//            println(prompt.take(1000) + "...")
//            return
//        }
//
//        println("\n🤖 Generating Frida script with Claude...")
//        val generated = llmClient.generateScript(prompt, maxTokens = 8192)
//
//        if (generated == null) {
//            println("❌ Failed to generate script")
//            return
//        }
//
//        println("\n✅ Script generated (${generated.tokensUsed} tokens used)")
//
//        // Salvar se solicitado
//        saveScript?.let { path ->
//            File(path).writeText(generated.script)
//            println("💾 Script saved to: $path")
//        }
//
//        // Mostrar script
//        println("\n📜 Generated Script:")
//        println("━".repeat(50))
//        println(generated.script)
//        println("━".repeat(50))
//
//        // Validar
//        val validation = executor.validateScript(generated.script)
//        validation.printReport()
//
//        if (!validation.valid) {
//            println("\n⚠️  Script has validation errors - execution skipped")
//            return
//        }
//
//        // Executar se não for dry-run
//        if (!dryRun) {
//            println("\n🚀 Executing script...")
//            val result = executor.execute(generated.script, durationSeconds = 20)
//            println(executor.formatOutput(result))
//        } else {
//            println("\n ℹ️  Dry-run mode - script not executed")
//        }
//    }

    private fun extractQueryHints(query: String): List<String> {
        val words = query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
            .filterNot { it in listOf("hook", "bypass", "method", "class", "return", "from", "with", "and", "the") }

        return words.distinct()
    }

    private fun runInteractiveMode(
        connector: FridaConnector,
        context: AppContext,
        router: QueryRouter,
        parser: QueryParser,
        collector: ContextCollector,
        promptBuilder: PromptBuilder,
        llmClient: LLMClient?,
        executor: ScriptExecutor
    ) {
        println("\n╔════════════════════════════════════════╗")
        println("║         Interactive Mode               ║")
        println("╚════════════════════════════════════════╝")
        println()
        println("💡 Tips:")
        println("  • Specific: 'hook com.example.Class.method() return false'")
        println("  • Generic:  'bypass emulator detection'")
        println("  • Commands: 'stats', 'classes', 'frameworks', 'help', 'exit'")

        if (llmClient == null) {
            println("\n⚠️  No API key - use 'setkey <key>' to enable LLM features")
        }
        println()

        var currentLLMClient = llmClient

        while (true) {
            print("frida-llm> ")
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
                    if (context.frameworks.isEmpty()) {
                        println("   No frameworks detected")
                    } else {
                        context.frameworks.forEach { fw ->
                            println("   ${fw.name} ${fw.version ?: ""} (${fw.type})")
                        }
                    }
                    continue
                }

                input.lowercase().startsWith("setkey ") -> {
                    val key = input.substring(7).trim()
                    currentLLMClient = LLMClient(key)
                    println("✅ API key set successfully")
                    continue
                }
            }

            // Process as query
            runBlocking {
                try {
                    if (currentLLMClient == null) {
                        println("⚠️  No API key set. Use 'setkey <your-key>' first")
                    } else {
                        processQuery(input, connector, collector, router, parser,
                            promptBuilder, currentLLMClient, executor)
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
        println("""
        
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
        
        Examples:
          hook com.example.SecurityCheck.isEmulator() return false
          bypass emulator detection
          intercept com.example.api.LoginService.login
          
        """.trimIndent())
    }

    private fun printBanner() {
        println("""
            ╔═══════════════════════════════════════════════╗
            ║     Frida LLM Tool - Research Project         ║
            ║     AI-Powered Mobile Instrumentation         ║
            ║                                               ║
            ║  🆕 Multi-Hook Support:                       ║
            ║     Hook multiple methods in one query!       ║
            ║                                               ║
            ║  Smart Query Routing:                         ║
            ║    ⚡ Specific   → 1-2s  (95% accuracy)        ║
            ║    🎯 Targeted   → 3-5s  (80% accuracy)       ║
            ║    🔎 Discovery  → 5-10s (70% accuracy)       ║
            ╚═══════════════════════════════════════════════╝
        """.trimIndent())
    }
}

fun main(args: Array<String>) {
    FridaLLMTool().main(args)
}
