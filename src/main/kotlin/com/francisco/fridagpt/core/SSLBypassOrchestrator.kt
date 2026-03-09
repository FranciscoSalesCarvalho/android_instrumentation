package com.francisco.fridagpt.core

import com.francisco.fridagpt.collectors.AppInfoCollector
import com.francisco.fridagpt.collectors.LibraryDetector
import com.francisco.fridagpt.llm.LLMClient
import com.francisco.fridagpt.models.AppInfo
import com.francisco.fridagpt.models.FrameworkType
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Orquestra todo o processo de SSL Pinning Bypass
 * Segue o fluxo completo: CA setup → Proxy → Context → LLM → Validate
 */
class SSLBypassOrchestrator(
    private val connector: FridaConnector,
    private val llmClient: LLMClient?,
    private val dryRun: Boolean = false
) {
    data class SSLBypassResult(
        var phaseSDK: PhaseResult = PhaseResult(),
        var phaseOkHttp: PhaseResult = PhaseResult(),
        var phasePrompt: PhaseResult = PhaseResult(),
        var phaseLLM: PhaseResult = PhaseResult(),
        var phaseValidation: PhaseResult = PhaseResult(),
        var generatedScript: String? = null,
        var scriptFile: String? = null,
    ) {
        val allPhasesComplete: Boolean
            get() = phaseSDK.success &&
                    phaseLLM.success &&
                    phaseValidation.success
    }

    data class PhaseResult(
        var success: Boolean = false,
        var message: String = "",
        var data: Any? = null
    )

    /**
     * Executa o fluxo completo de SSL Bypass
     */
    suspend fun execute(
        saveScriptPath: String? = null,
        stacktrace: String,
    ): SSLBypassResult {
        val result = SSLBypassResult()

        printHeader()

        // PHASE 1: Collect SDK Information
        println("\n" + "═".repeat(70))
        println("PHASE 1/5: Collect SDK & App Information")
        println("═".repeat(70))
        result.phaseSDK = collectSDKInfo()

        if (!result.phaseSDK.success) {
            printPhaseFailure(1, result.phaseSDK.message)
            if (!askContinue()) return result
        } else {
            printPhaseSuccess(1)
            val appInfo = result.phaseSDK.data as? AppInfo
            appInfo?.let {
                println("   📱 Package: ${it.packageName}")
                println("   📊 Target SDK: ${it.targetSdk}")
                println("   🐛 Debuggable: ${it.isDebuggable}")
            }
        }

        if (!stacktrace.isEmpty()) {
            // PHASE 2: Detect OkHttp
            println("\n" + "═".repeat(70))
            println("PHASE 2/5: Detect Networking Frameworks")
            println("═".repeat(70))
            result.phaseOkHttp = detectOkHttp()

            // OkHttp is optional - not blocking
            if (result.phaseOkHttp.success) {
                printPhaseSuccess(2)
                val frameworks = result.phaseOkHttp.data as? List<*>
                frameworks?.forEach { fw ->
                    println("   🔧 $fw")
                }
            } else {
                println("⚠️  No common frameworks detected (will use generic bypass)")
                result.phaseOkHttp.success = true // Non-blocking
            }
        }

        // PHASE 3: Generate Prompt
        println("\n" + "═".repeat(70))
        println("PHASE 3/5: Generate LLM Prompt")
        println("═".repeat(70))
        result.phasePrompt = generatePrompt(result, stacktrace)

        if (!result.phasePrompt.success) {
            printPhaseFailure(3, result.phasePrompt.message)
            return result
        } else {
            printPhaseSuccess(3)
            val promptLength = (result.phasePrompt.data as? String)?.length ?: 0
            println("   📝 Prompt size: $promptLength chars")
        }

        // PHASE 4: Send to LLM
        println("\n" + "═".repeat(70))
        println("PHASE 4/5: Generate Script with LLM")
        println("═".repeat(70))
        result.phaseLLM = generateScript(result.phasePrompt.data as String)

        if (!result.phaseLLM.success) {
            printPhaseFailure(4, result.phaseLLM.message)
            return result
        } else {
            printPhaseSuccess(4)
            result.generatedScript = result.phaseLLM.data as String
            val scriptLines = result.generatedScript?.lines()?.size ?: 0
            println("   📜 Script: $scriptLines lines")
        }

        // PHASE 5: Validate Script
        println("\n" + "═".repeat(70))
        println("PHASE 5/5: Validate Generated Script")
        println("═".repeat(70))
        result.phaseValidation = validateScript(result.generatedScript!!)

        if (!result.phaseValidation.success) {
            printPhaseFailure(5, result.phaseValidation.message)
            println("\n⚠️  Script has validation errors but may still work")
            if (!askContinue("Execute anyway?")) return result
        } else {
            printPhaseSuccess(5)
        }

        // Save script if requested
        saveScriptPath?.let { path ->
            saveScript(result.generatedScript!!, path)
            result.scriptFile = path
            println("\n💾 Script saved to: $path")
        }

        // Summary
        printSummary(result)

        return result
    }

    private fun printHeader() {
        println()
        println("╔" + "═".repeat(68) + "╗")
        println("║" + " SSL Pinning Bypass - Complete Workflow ".center(68) + "║")
        println("║" + " 7 Phases: CA → Proxy → Context → LLM → Validate ".center(68) + "║")
        println("╚" + "═".repeat(68) + "╝")
    }

    private suspend fun collectSDKInfo(): PhaseResult {
        return try {
            println("📊 Collecting app information...")
            println()

            val appInfoCollector = AppInfoCollector(connector)
            val appInfo = appInfoCollector.collect()

            if (appInfo != null) {
                PhaseResult(
                    success = true,
                    message = "App info collected",
                    data = appInfo
                )
            } else {
                PhaseResult(
                    success = false,
                    message = "Failed to collect app information"
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "SDK info collection failed" }
            PhaseResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    private suspend fun detectOkHttp(): PhaseResult {
        return try {
            println("🔍 Detecting networking frameworks...")
            println()

            val libraryDetector = LibraryDetector(connector)
            val frameworks = libraryDetector.detect()

            val networkingFws = frameworks.filter {
                it.type == FrameworkType.NETWORKING
            }

            if (networkingFws.isNotEmpty()) {
                PhaseResult(
                    success = true,
                    message = "${networkingFws.size} networking framework(s) detected",
                    data = networkingFws
                )
            } else {
                PhaseResult(
                    success = false,
                    message = "No networking frameworks detected",
                    data = emptyList<Any>()
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Framework detection failed" }
            PhaseResult(
                success = false,
                message = e.message ?: "Unknown error",
                data = emptyList<Any>()
            )
        }
    }

    private fun generatePrompt(
        result: SSLBypassResult,
        stacktrace: String,
    ): PhaseResult {
        return try {
            println("✍️  Generating optimized prompt for LLM...")
            println()

            val appInfo = result.phaseSDK.data as? AppInfo
            val frameworks = result.phaseOkHttp.data as? List<*>

            val prompt = buildSSLBypassPrompt(appInfo, frameworks, stacktrace)

            PhaseResult(
                success = true,
                message = "Prompt generated",
                data = prompt
            )
        } catch (e: Exception) {
            logger.error(e) { "Prompt generation failed" }
            PhaseResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    private suspend fun generateScript(prompt: String): PhaseResult {
        return try {
            if (llmClient == null) {
                return PhaseResult(
                    success = false,
                    message = "No LLM client configured (API key missing)"
                )
            }

            if (dryRun) {
                println("🔍 DRY RUN MODE - Showing prompt instead of calling LLM")
                println()
                println("━".repeat(70))
                println(prompt.take(500) + "...")
                println("━".repeat(70))
                return PhaseResult(
                    success = false,
                    message = "Dry run mode - script not generated"
                )
            }

            println("🤖 Sending request to Claude API...")
            println("   (This may take 10-30 seconds)")
            println()

            val generated = llmClient.generateScript(prompt, maxTokens = 8192)

            if (generated != null) {
                PhaseResult(
                    success = true,
                    message = "Script generated (${generated.tokensUsed} tokens)",
                    data = generated.script
                )
            } else {
                PhaseResult(
                    success = false,
                    message = "LLM failed to generate script"
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "LLM generation failed" }
            PhaseResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    private fun validateScript(script: String): PhaseResult {
        return try {
            println("🔍 Validating generated script...")
            println()

            val executor = ScriptExecutor(connector)
            val validation = executor.validateScript(script)

            validation.errors.forEach { error ->
                println("   ❌ $error")
            }

            validation.warnings.forEach { warning ->
                println("   ⚠️  $warning")
            }

            PhaseResult(
                success = validation.valid,
                message = if (validation.valid) {
                    "Script is valid"
                } else {
                    "${validation.errors.size} error(s), ${validation.warnings.size} warning(s)"
                },
                data = validation
            )
        } catch (e: Exception) {
            logger.error(e) { "Validation failed" }
            PhaseResult(success = false, message = e.message ?: "Unknown error")
        }
    }

    // ============ Helper Methods ============

    private fun buildSSLBypassPrompt(
        appInfo: AppInfo?,
        frameworks: List<*>?,
        stacktrace: String?,
    ): String {
        return """
You are an expert in Frida dynamic instrumentation for Android SSL Pinning bypass.

APP INFORMATION:
${
            appInfo?.let {
                """
- Package: ${it.packageName}
- Target SDK: ${it.targetSdk}
- Debuggable: ${it.isDebuggable}
"""
            } ?: "- No app info available"
        }

DETECTED FRAMEWORKS:
${frameworks?.joinToString("\n") { "- $it" } ?: "- No specific frameworks detected"}

STACKTRACE:
$stacktrace

TASK:
Generate a comprehensive Frida script to bypass SSL pinning for this Android application.

REQUIREMENTS:
1. Use Java.perform() wrapper
2. write the simplest possible bypasses

EXAMPLE STRUCTURE:
```javascript
Java.perform(function() {
    console.log("[+] SSL Pinning Bypass Script Loaded");
    console.log("[+] Target: ${appInfo?.packageName ?: "Unknown"}");
    
    // Bypass 1: OkHttp CertificatePinner
    try {
        // ... implementation
    } catch(e) {
        console.log("[-] Bypass 1 failed: " + e);
    }
    
    // Bypass 2: TrustManager
    try {
        // ... implementation
    } catch(e) {
        console.log("[-] Bypass 2 failed: " + e);
    }
    
    // ... more bypasses
    
    console.log("[+] SSL Bypass script loaded successfully");
});
```

OUTPUT:
Provide ONLY the JavaScript code, no explanations before or after.
Make it comprehensive and production-ready.
        """.trimIndent()
    }

    private fun printPhaseSuccess(phase: Int) {
        println()
        println("✅ Phase $phase/8 completed successfully")
    }

    private fun printPhaseFailure(phase: Int, message: String) {
        println()
        println("❌ Phase $phase/8 failed: $message")
    }

    private fun printSummary(result: SSLBypassResult) {
        println("\n" + "═".repeat(70))
        println("SUMMARY")
        println("═".repeat(70))
        println()
        println("Phase Results:")
        println("  ${if (result.phaseSDK.success) "✅" else "❌"} 1. SDK Information")
        println("  ${if (result.phaseOkHttp.success) "✅" else "⚠️ "} 2. Framework Detection (optional)")
        println("  ${if (result.phasePrompt.success) "✅" else "❌"} 3. Prompt Generation")
        println("  ${if (result.phaseLLM.success) "✅" else "❌"} 4. LLM Script Generation")
        println("  ${if (result.phaseValidation.success) "✅" else "⚠️ "} 5. Script Validation")
        println()

        if (result.allPhasesComplete) {
            println("🎉 All critical phases completed successfully!")
            println()
            println("Next Steps:")
            println("  1. Review the generated script")
            println("  2. Execute: frida -U -f PACKAGE -l ${result.scriptFile ?: "script.js"} --no-pause")
            println("  3. Open the app and test HTTPS requests")
            println("  4. Check Burp/mitmproxy for decrypted traffic")
        } else {
            println("⚠️  Some phases failed - script may not work correctly")
            println("   Review errors above and fix issues before executing")
        }

        println()
        println("═".repeat(70))
    }

    private fun saveScript(script: String, path: String) {
        java.io.File("$path/test.js").writeText(script)
    }

    private fun askContinue(message: String = "Continue anyway?"): Boolean {
        print("\n$message (y/N): ")
        val response = readLine()?.trim()?.lowercase()
        return response == "y" || response == "yes"
    }
}

private fun String.center(width: Int): String {
    if (this.length >= width) return this
    val padding = (width - this.length) / 2
    return " ".repeat(padding) + this + " ".repeat(width - this.length - padding)
}