package com.francisco.fridagpt.llm

import com.francisco.fridagpt.models.AppContext
import com.francisco.fridagpt.models.ClassCategory
import com.francisco.fridagpt.models.ClassInfo
import com.francisco.fridagpt.models.NativeContext

/**
 * Constrói prompts otimizados para o LLM
 */
class PromptBuilder {

    /**
     * Prompt para query específica (classe e método conhecidos)
     */
    fun buildSpecificPrompt(query: String): String {
        return """
            You are an expert in Frida dynamic instrumentation for Android.
            
            Generate a Frida script in JavaScript to accomplish the following task:
            $query
                
            ${buildRequirements()}
            
            OUTPUT:
            Provide ONLY the JavaScript code, no explanations before or after.
        """.trimIndent()
    }

    /**
     * Prompt para query com contexto (classes relevantes conhecidas)
     */
    fun buildContextualPrompt(
        query: String,
        relevantClasses: List<ClassInfo>,
        context: AppContext
    ): String {
        val classesInfo = relevantClasses.joinToString("\n") { classInfo ->
            val methodsInfo = if (classInfo.methods.isNotEmpty()) {
                classInfo.methods.take(10).joinToString("\n      ") {
                    "- ${it.signature}"
                }
            } else {
                "  (methods not collected)"
            }
            """
              Class: ${classInfo.name}
                Methods:
                  $methodsInfo
            """.trimIndent()
        }

        return """
            You are an expert in Frida dynamic instrumentation for Android.
            
            USER REQUEST:
            "$query"
            
            APP CONTEXT:
            - Package: ${context.appInfo.packageName}
            - Debuggable: ${context.appInfo.isDebuggable}
            
            DETECTED FRAMEWORKS:
            ${context.libraries.take(5).joinToString("\n") { "- ${it.name} ${it.version ?: ""} (${it.type})" }}
            
            RELEVANT CLASSES FOUND:
            $classesInfo
            
            TASK:
            Analyze the user request and the available classes/methods, then generate a Frida script to accomplish the task.
            
            ${buildRequirements()}
            
            OUTPUT:
            Provide ONLY the JavaScript code, no explanations.
        """.trimIndent()
    }

    /**
     * Prompt para query genérica (descoberta completa)
     */
    fun buildGenericPrompt(
        query: String,
        context: AppContext,
        stacktrace: String? = null
    ): String {
        val appClasses = context.classes
            .filter {
                it.category == ClassCategory.APP &&
                        !it.name.contains("$") &&
                        !it.name.endsWith("kt", ignoreCase = true)
            }

        val classesInfo = appClasses.joinToString("\n") { classInfo ->
            val methodsInfo = if (classInfo.methods.isNotEmpty()) {
                classInfo.methods
                    .filter { !it.name.contains("$") }
                    .joinToString("\n      ") {
                    "- ${it.signature}"
                }
            } else {
                "  (methods not collected)"
            }
            """
              Class: ${classInfo.name}
                Methods:
                  $methodsInfo
            """.trimIndent()
        }

        val nativeSection = buildNativeSection(context.nativeContext)
        val stacktraceSection = buildStacktraceSection(stacktrace)

        return """
            You are an expert in Frida dynamic instrumentation for Android.
            
            USER REQUEST:
            "$query"
            
            APP INFORMATION:
            - Package: ${context.appInfo.packageName}
            - Min SDK: ${context.appInfo.minSdk}
            - Target SDK: ${context.appInfo.targetSdk}
            - Debuggable: ${context.appInfo.isDebuggable}
            
            DETECTED FRAMEWORKS:
            ${context.libraries.joinToString("\n") { "- ${it.name} ${it.version ?: ""} (${it.type})" }}
            
            RELEVANT CLASSES FOUND:
            $classesInfo
            
            $nativeSection
            
            TASK:
            Based on the user request and app information, generate a Frida script to accomplish the goal.
            You may need to make educated guesses about which classes/methods to hook based on common Android patterns.
            
            For example:
            - Emulator detection: often in Application class, SecurityCheck, DeviceValidator classes
            - Root detection: RootChecker, SecurityManager classes  
            - SSL Pinning: OkHttpClient, CertificatePinner, custom network classes
            
            $stacktraceSection
            
            ${buildRequirements()}
            
            OUTPUT:
            Provide ONLY the JavaScript code, no explanations.
        """.trimIndent()
    }

    /**
     * Gera a seção de contexto nativo para inclusão no prompt.
     *
     * Prioriza informações de proteção (o que realmente importa para o LLM
     * gerar scripts que lidem com anti-análise), seguido de métodos JNI
     * e resumo de módulos.
     *
     * @param nativeContext contexto nativo coletado, ou null se indisponível
     * @return string formatada para injeção no prompt, ou vazia se sem contexto
     */
    private fun buildNativeSection(nativeContext: NativeContext?): String {
        if (nativeContext == null) return ""

        val sb = StringBuilder()
        sb.appendLine()
        sb.appendLine("NATIVE CODE CONTEXT:")
        sb.appendLine("- Architecture: ${nativeContext.arch}")
        sb.appendLine("- Pointer size: ${nativeContext.pointerSize} bytes")
        sb.appendLine("- Modules: ${nativeContext.summary.app} app / ${nativeContext.summary.total} total")

        // 1. Proteções detectadas (prioridade máxima para o LLM)
        if (nativeContext.protections.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("NATIVE PROTECTIONS DETECTED:")

            val byCategory = nativeContext.protections.groupBy { it.category }
            byCategory.forEach { (category, detections) ->
                sb.appendLine("  [$category]")
                detections.forEach { det ->
                    sb.appendLine("    - ${det.func} @ ${det.module} (${det.address})")
                }
            }
        }

        // 2. Módulos do app com exports
        if (nativeContext.modules.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("APP NATIVE MODULES (${nativeContext.modules.size}):")
            nativeContext.modules.forEach { mod ->
                sb.appendLine("  - ${mod.name} (${formatSize(mod.size)}, ${mod.exports.size} exports)")
                if (mod.exports.isNotEmpty()) {
                    mod.exports.take(30).forEach { exp ->
                        sb.appendLine("      $exp")
                    }
                    if (mod.exports.size > 30) {
                        sb.appendLine("      ... and ${mod.exports.size - 30} more")
                    }
                }
            }
        }

        // 3. Instrução ao LLM se proteções detectadas
        if (nativeContext.protections.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("IMPORTANT: This application has native-level protections.")
            sb.appendLine("The generated script MUST account for these protections.")
            sb.appendLine("Use Interceptor.attach() with Module.findExportByName() to hook native functions.")
            sb.appendLine("Consider bypassing detection mechanisms BEFORE performing the requested task.")
        }

        return sb.toString()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
        }
    }

    private fun buildStacktraceSection(stacktrace: String?): String {
        if (stacktrace.isNullOrBlank()) return ""

        return """
            
            STACK TRACE (captured from app execution):
            $stacktrace
            
            Use this stack trace to identify the exact call chain and target the most
            appropriate methods for hooking. The stack trace reveals the actual execution
            flow of the application.
        """.trimIndent()
    }

    companion object {
        fun buildRequirements(): String {
            return """
            REQUIREMENTS:
            1. Use Java.perform() wrapper
            2. Hook the most appropriate method(s) to achieve the goal
            3. Include error handling (try-catch)
            4. Add informative console.log statements
            5. If multiple methods need to be hooked, hook all of them
            6. Keep code clean and well-commented
            7. When passing string literals to Android methods (Toast, TextView, Intent, etc.), always convert to Java String:
               - CORRECT: Java.use("java.lang.String").${'$'}new("message")
               - WRONG: "message" (JavaScript literal)
            8. When replacing void method implementations:
               - Do NOT call the original method (causes infinite recursion)
               - Do NOT use return statement
               - Simply implement the new behavior
            9. When replacing non-void method implementations and you need to call the original:
                - Use this.methodName.call(this, args) or store original reference before hooking
            10. Avoid using Java.scheduleOnMainThread() unless strictly necessary:
                - Methods called from UI events (button clicks, etc.) are already on main thread
                - If needed, capture 'this' reference before the closure
            11. When hooking Android framework interfaces, hook the concrete implementation class instead:
                - SharedPreferences${'$'}Editor → android.app.SharedPreferencesImpl${'$'}EditorImpl
                - ContentResolver → android.content.ContentResolver (concrete class)
                - Abstract/interface methods cannot be hooked directly
                - Common implementations:
                  * SharedPreferences${'$'}Editor.putString() → android.app.SharedPreferencesImpl${'$'}EditorImpl.putString()
                  * SharedPreferences${'$'}Editor.commit() → android.app.SharedPreferencesImpl${'$'}EditorImpl.commit()
                  * SharedPreferences${'$'}Editor.apply() → android.app.SharedPreferencesImpl${'$'}EditorImpl.apply()
            12. Avoid overload any method from java api. Is too easy to make the application crash when done.
            13. When asked to bypass password replace true stick to boolean methods.
            14. NEVER hook high-volume runtime methods globally. These are called thousands
                of times per second and will flood the output, making observation impossible:
                - String.hashCode(), String.getBytes(), String.equals()
                - Object.toString(), Object.hashCode()
                - Arrays.toString(), Arrays.equals()
                - If you need to observe these, hook them only on specific instances or
                  within a specific call path, never on the base class.
            15. NEVER hook methods within another hook's implementation body. When you
                hook getInstance() and inside its body you redefine update() and digest(),
                those redefinitions can stack across calls and cause infinite recursion.
                If you need to observe a returned instance, hook the class methods once
                at script load time (outside any other hook).
            16. NEVER use Java.enumerateLoadedClasses with Java.use() calls on matched
                classes. Enumerating and instantiating hundreds of wrappers at runtime
                freezes the app. If you need to discover classes, use static analysis
                ahead of time and hardcode the targets in the script.
            17. NEVER inspect Thread.currentThread().getStackTrace() inside a hook that
                fires on a high-volume method. Stack trace inspection is expensive and
                multiplies the impact of an already-frequent hook.
            18. Prefer narrow, targeted hooks over broad coverage. When given a choice
                between:
                (a) hooking one specific method that is known to be called by the target
                (b) hooking every class that might possibly be relevant
                always prefer (a). Broad coverage generates noise that masks the signal.
            19. When hooking Context methods like sendBroadcast, registerReceiver, or startActivity, 
                hook the concrete implementation class (ContextImpl or ContextWrapper) instead of the 
                abstract Context class, as hooks on abstract classes may not intercept actual calls.
        """.trimIndent()
        }
    }
}
