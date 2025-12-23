@file:JvmName("Main")

package com.francisco.fridagpt

import com.francisco.fridagpt.collectors.LogAnalyzer
import com.francisco.fridagpt.collectors.StorageCollector
import com.francisco.fridagpt.core.*
import com.francisco.fridagpt.core.SSLBypassOrchestrator.PhaseResult
import com.francisco.fridagpt.llm.LLMClient
import com.francisco.fridagpt.llm.PromptBuilder
import com.francisco.fridagpt.models.MethodInfo
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

    private val output by option(
        "-o", "--output",
        help = "Output file for context JSON"
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

    private val setupCertificates by option(
        "--certificates",
        help = "Setup user and system certificates"
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
            val executor = ScriptExecutor(connector)
            val llmClient = if (key != null) LLMClient(key) else null
            val collector = ContextCollector(connector)

            if (analyzeLogs) {
                logger.info { "Starting log analysis..." }
                val analyzer = LogAnalyzer(connector)
                val result = analyzer.analyzeLogs(logDuration)
                println(analyzer.generateReport(result))
                return@runBlocking
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
                    executor = executor
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
                    executor = executor
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

                // Detectar se é SSL Pinning bypass
                val isSSLBypass = query.lowercase().let { q ->
                    q.contains("ssl") || q.contains("pinning") ||
                            q.contains("certificate") || q.contains("certificatepinner")
                }

                // Se for SSL bypass, usar o orquestrador especializado
                if (isSSLBypass) {
                    handleSSLBypass(connector, llmClient, executor)
                    return
                }

                handleGenericQuery(
                    query = query,
                    collector = collector,
                    promptBuilder = promptBuilder,
                    llmClient = llmClient,
                    executor = executor
                )
            }
        }
    }

    /**
     * Fluxo especializado para SSL Pinning Bypass
     */
    private suspend fun handleSSLBypass(
        connector: FridaConnector,
        llmClient: LLMClient?,
        executor: ScriptExecutor
    ) {
        println("\n🔒 SSL Pinning Bypass Mode - Using specialized workflow")
        println()

        if (setupCertificates) {
            println("\n" + "═".repeat(70))
            println("User & System CA Certificate Setup")
            println("═".repeat(70))
            val certificates = setupCACertificates()

            if (!certificates.success) {
                println("❌ Setup certificates failed: ${certificates.message}")
                return
            } else {
                println("✅ Setup certificates completed successfully")
            }

            println("\n" + "═".repeat(70))
            println("Proxy Configuration")
            println("═".repeat(70))
            val proxy = setupProxy()

            if (!proxy.success) {
                println("❌ Proxy configuration failed: ${proxy.message}")
                return
            } else {
                println("✅ Setup proxy completed successfully")
            }
        }

        val orchestrator = SSLBypassOrchestrator(
            connector = connector,
            llmClient = llmClient,
            dryRun = dryRun
        )
        val content = File(stacktrace.orEmpty()).runCatching {
            readText()
        }.getOrDefault("")
        val result = orchestrator.execute(saveScript, content)

        // Se tudo OK e não é dry-run, executar
        if (result.allPhasesComplete && !dryRun && result.generatedScript != null) {
            println()
            print("🚀 Ready to execute SSL bypass script. Proceed? (Y/n): ")
            val proceed = readLine()?.trim()?.lowercase()

            if (proceed != "n" && proceed != "no") {
                println("\n🚀 Executing script...")
                val execResult = executor.execute(result.generatedScript!!, durationSeconds = 20)
                println(executor.formatOutput(execResult))

                if (execResult.success) {
                    println(
                        """
                    
                    ╔════════════════════════════════════════════════════════╗
                    ║         SSL Bypass Active - Testing Instructions       ║
                    ╚════════════════════════════════════════════════════════╝
                    
                    ✅ Frida script is running
                    ✅ Proxy is configured
                    ✅ CA certificate installed
                    
                    📱 Next Steps:
                    1. Open the target app on your device
                    2. Login or perform actions that use HTTPS
                    3. Check Burp/mitmproxy for decrypted traffic
                    
                    ✅ Success indicators:
                       • HTTPS requests visible in proxy
                       • Traffic is decrypted (readable JSON/XML)
                       • App works normally
                    
                    ❌ Failure indicators:
                       • No traffic in proxy
                       • SSL/certificate errors in app
                       • App crashes
                    
                    Press Ctrl+C to stop when done testing
                    
                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    
                    """.trimIndent()
                    )
                }
            }
        }

        // Cleanup prompt
        if (!dryRun) {
            println()
            print("Remove proxy configuration? (y/N): ")
            val cleanup = readLine()?.trim()?.lowercase()
            if (cleanup == "y" || cleanup == "yes") {
                val proxyManager = ProxyManager(device)
                proxyManager.cleanup()
                println("✅ Proxy configuration removed")
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

    private suspend fun setupCACertificates(): PhaseResult {
        return try {
            println("📜 Setting up User CA certificate...")
            println()

            val caInstaller = CAInstaller(device)
            val caResult = caInstaller.setupBurpCertificate()

            if (caResult.isComplete) {
                PhaseResult(
                    success = true,
                    message = "User CA certificate installed",
                    data = caResult
                )
            } else {
                PhaseResult(
                    success = false,
                    message = "User CA setup incomplete: " +
                            "Burp=${caResult.burpRunning}, " +
                            "Downloaded=${caResult.certDownloaded}, " +
                            "Installed=${caResult.certInstalled}",
                    data = caResult
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "User CA setup failed" }
            PhaseResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    private suspend fun setupProxy(): PhaseResult {
        return try {
            println("📡 Configuring proxy on device...")
            println()

            val proxyManager = ProxyManager(device)
            val proxyResult = proxyManager.setupForSSLBypass()

            if (proxyResult.isReady) {
                PhaseResult(
                    success = true,
                    message = "Proxy configured and reachable",
                    data = proxyResult
                )
            } else {
                PhaseResult(
                    success = false,
                    message = "Proxy setup incomplete: " +
                            "Configured=${proxyResult.proxyConfigured}, " +
                            "Reachable=${proxyResult.proxyReachable}",
                    data = proxyResult
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Proxy setup failed" }
            PhaseResult(success = false, message = e.message ?: "Unknown error")
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
        val context = collector.collectFullContext()

        if (context == null) {
            println("❌ Failed to collect context")
            return
        }

        // Mostrar estatísticas
        collector.printStats(context)

        // Salvar JSON se especificado
        output?.let { path ->
            val jsonStr = Json { prettyPrint = true }.encodeToString(context)

            File(path).writeText(jsonStr)
            logger.info { "Context saved to: $path" }
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

        println("\n🤖 Generating Frida script with Claude...")
        val generated = llmClient.generateScript(prompt, maxTokens = 8192)

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
        executor: ScriptExecutor
    ) {
        println("\n📊 Collecting full context for generic query...")

        val context = collector.collectForQuery(query)

        if (context == null) {
            println("❌ Failed to collect context")
            return
        }

        collector.printStats(context)

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
            needsMultipleHooks = needsMultiHooks,
        )

        if (llmClient == null) {
            println("\n📝 Prompt that would be sent:")
            println("━".repeat(50))
            println(prompt.take(1000) + "...")
            return
        }

        println("\n🤖 Generating Frida script with Claude...")
        val generated = llmClient.generateScript(prompt, maxTokens = 8192)

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
            val result = executor.execute(generated.script, durationSeconds = 20)
            println(executor.formatOutput(result))
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

        val context = collector.collectFullContext()
        if (context == null) {
            logger.error { "Failed to collect context" }
            return
        }

        // Mostrar estatísticas
        collector.printStats(context)

        // Salvar JSON se especificado
        output?.let { path ->
            val jsonStr = Json { prettyPrint = true }.encodeToString(context)

            File(path).writeText(jsonStr)
            logger.info { "Context saved to: $path" }
        }

        println()

        while (true) {
            print("fridagpt> ")
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
                            executor = executor
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
        
        Examples:
          hook com.example.SecurityCheck.isEmulator() return false
          bypass emulator detection
          intercept com.example.api.LoginService.login
          
        """.trimIndent()
        )
    }

    private fun printBanner() {
        println(
            """
            ╔═══════════════════════════════════════════════╗
            ║     Frida LLM Tool - Research Project         ║
            ║     AI-Powered Mobile Instrumentation         ║
            ║                                               ║
            ║     Multi-Hook Support:                       ║
            ║     Hook multiple methods in one query!       ║
            ║                                               ║
            ║  Smart Query Routing:                         ║
            ║    ⚡ Specific   → 1-2s  (95% accuracy)        ║
            ║    🎯 Targeted   → 3-5s  (80% accuracy)       ║
            ║    🔎 Discovery  → 5-10s (70% accuracy)       ║
            ╚═══════════════════════════════════════════════╝
        """.trimIndent()
        )
    }
}

fun main(args: Array<String>) {
    FridaLLMTool().main(args)
}
