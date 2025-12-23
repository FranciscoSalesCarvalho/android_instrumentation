package com.francisco.fridagpt.llm

import com.francisco.fridagpt.core.ActionType
import com.francisco.fridagpt.core.MultiHookQuery
import com.francisco.fridagpt.core.ParsedQuery
import com.francisco.fridagpt.models.AppContext
import com.francisco.fridagpt.models.ClassCategory
import com.francisco.fridagpt.models.ClassInfo

/**
 * Constrói prompts otimizados para o LLM
 */
class PromptBuilder {

    /**
     * Prompt para query específica (classe e método conhecidos)
     */
    fun buildSpecificPrompt(parsed: ParsedQuery): String {
        return """
            You are an expert in Frida dynamic instrumentation for Android.
            
            Generate a Frida script in JavaScript to accomplish the following task:
            ${parsed.originalQuery}
                
            REQUIREMENTS:
            1. Use Java.perform() wrapper
            2. Hook the exact method specified
            3. Include error handling (try-catch)
            4. Add console.log statements to show when hook is triggered
            5. Log method parameters when called
            6. Implement the required action
            7. Keep code clean and well-commented
            8. When passing string literals to Android methods (Toast, TextView, Intent, etc.), always convert to Java String:
               - CORRECT: Java.use("java.lang.String").${'$'}new("message")
               - WRONG: "message" (JavaScript literal)
            9. When replacing void method implementations:
               - Do NOT call the original method (causes infinite recursion)
               - Do NOT use return statement
               - Simply implement the new behavior
            10. When replacing non-void method implementations and you need to call the original:
                - Use this.methodName.call(this, args) or store original reference before hooking
            11. Avoid using Java.scheduleOnMainThread() unless strictly necessary:
                - Methods called from UI events (button clicks, etc.) are already on main thread
                - If needed, capture 'this' reference before the closure
            12. When hooking Android framework interfaces, hook the concrete implementation class instead:
                - SharedPreferences${'$'}Editor → android.app.SharedPreferencesImpl${'$'}EditorImpl
                - ContentResolver → android.content.ContentResolver (concrete class)
                - Abstract/interface methods cannot be hooked directly
                - Common implementations:
                  * SharedPreferences${'$'}Editor.putString() → android.app.SharedPreferencesImpl${'$'}EditorImpl.putString()
                  * SharedPreferences${'$'}Editor.commit() → android.app.SharedPreferencesImpl${'$'}EditorImpl.commit()
                  * SharedPreferences${'$'}Editor.apply() → android.app.SharedPreferencesImpl${'$'}EditorImpl.apply()
            
            OUTPUT:
            Provide ONLY the JavaScript code, no explanations before or after.
        """.trimIndent()
    }

    /**
     * Prompt para múltiplos hooks
     */
    fun buildMultiHookPrompt(multiHook: MultiHookQuery): String {
        val hooksDescription = multiHook.hooks.mapIndexed { index, hook ->
            """
                Hook ${index + 1}:
                  - Class: ${hook.className}
                  - Method: ${hook.methodName}
                  ${if (hook.parameters.isNotEmpty()) "- Parameters: ${hook.parameters.joinToString(", ")}" else ""}
                  - Action: ${getActionDescription(hook.action, hook.returnValue)}
            """.trimIndent()
        }.joinToString("\n\n")

        return """
                You are an expert in Frida dynamic instrumentation for Android.
                
                Generate a Frida script in JavaScript to accomplish ALL of the following tasks:
                
                TARGETS (${multiHook.hooks.size} hooks required):
                $hooksDescription
                
                REQUIREMENTS:
                1. Use a single Java.perform() wrapper for all hooks
                2. Hook ALL ${multiHook.hooks.size} methods specified above
                3. Include error handling (try-catch) for each hook
                4. Add console.log statements to show when each hook is triggered
                5. Log the class and method name for each hook
                6. Implement the required action for each method
                7. If any hook fails, continue with the others (don't crash the script)
                8. Keep code clean and well-commented
                9. Number each hook (// Hook 1, // Hook 2, etc.)
                
                EXAMPLE STRUCTURE:
                ```javascript
                Java.perform(function() {
                    console.log("[+] Starting multi-hook script");
                    
                    // Hook 1: Class1.method1
                    try {
                        var Class1 = Java.use("...");
                        Class1.method1.implementation = function() {
                            console.log("[Hook 1] Class1.method1 called");
                            // action
                        };
                    } catch(e) {
                        console.error("[Hook 1] Failed: " + e);
                    }
                    
                    // Hook 2: Class2.method2
                    try {
                        var Class2 = Java.use("...");
                        Class2.method2.implementation = function() {
                            console.log("[Hook 2] Class2.method2 called");
                            // action
                        };
                    } catch(e) {
                        console.error("[Hook 2] Failed: " + e);
                    }
                    
                    console.log("[+] All hooks installed");
                });
                ```
                
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
        context: AppContext,
        needsMultipleHooks: Boolean = false
    ): String {
        val classesInfo = relevantClasses.take(5).joinToString("\n") { classInfo ->
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
            ${context.frameworks.take(5).joinToString("\n") { "- ${it.name} ${it.version ?: ""} (${it.type})" }}
            
            RELEVANT CLASSES FOUND:
            $classesInfo
            
            TASK:
            Analyze the user request and the available classes/methods, then generate a Frida script to accomplish the task.
            
            ${
            if (needsMultipleHooks) """
            IMPORTANT - MULTIPLE HOOKS REQUIRED:
            The user's request suggests hooking multiple related methods. Consider:
            1.Hook just the methods asked by the user.
                """ 
                else ""
            }
            REQUIREMENTS:
            1. Use Java.perform() wrapper
            2. Hook the most appropriate method(s) to achieve the goal
            3. Include error handling
            4. Add informative console.log statements
            5. If multiple methods need to be hooked, hook all of them
            6. Consider the frameworks detected when writing the script
            7. Be specific - use the actual class and method names from the context
            8. When passing string literals to Android methods (Toast, TextView, Intent, etc.), always convert to Java String:
               - CORRECT: Java.use("java.lang.String").${'$'}new("message")
               - WRONG: "message" (JavaScript literal)
            9. When replacing void method implementations:
               - Do NOT call the original method (causes infinite recursion)
               - Do NOT use return statement
               - Simply implement the new behavior
            10. When replacing non-void method implementations and you need to call the original:
                - Use this.methodName.call(this, args) or store original reference before hooking
            11. Avoid using Java.scheduleOnMainThread() unless strictly necessary
                - Methods called from UI events (button clicks, etc.) are already on main thread
            12. When hooking Android framework interfaces, hook the concrete implementation class instead:
                - SharedPreferences${'$'}Editor → android.app.SharedPreferencesImpl${'$'}EditorImpl
                - ContentResolver → android.content.ContentResolver (concrete class)
                - Abstract/interface methods cannot be hooked directly
                - Common implementations:
                  * SharedPreferences${'$'}Editor.putString() → android.app.SharedPreferencesImpl${'$'}EditorImpl.putString()
                  * SharedPreferences${'$'}Editor.commit() → android.app.SharedPreferencesImpl${'$'}EditorImpl.commit()
                  * SharedPreferences${'$'}Editor.apply() → android.app.SharedPreferencesImpl${'$'}EditorImpl.apply()

            
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
        needsMultipleHooks: Boolean = false
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
                    .take(10)
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
            ${context.frameworks.joinToString("\n") { "- ${it.name} ${it.version ?: ""} (${it.type})" }}
            
            RELEVANT CLASSES FOUND:
            $classesInfo
            
            TASK:
            Based on the user request and app information, generate a Frida script to accomplish the goal.
            You may need to make educated guesses about which classes/methods to hook based on common Android patterns.
            
            ${
                if (needsMultipleHooks) """
                IMPORTANT - MULTIPLE HOOKS REQUIRED:
                The user's request suggests hooking multiple related methods. Consider:
                1.Hook just the methods asked by the user.
                    """
                else ""
            }
            
            For example:
            - Emulator detection: often in Application class, SecurityCheck, DeviceValidator classes
            - Root detection: RootChecker, SecurityManager classes  
            - SSL Pinning: OkHttpClient, CertificatePinner, custom network classes
            
            REQUIREMENTS:
            1. Use Java.perform() wrapper
            2. Hook appropriate methods to achieve the goal
            3. Include error handling for class/method not found
            4. Add console.log statements
            5. Try multiple common patterns if needed
            6. Comment the code explaining what each hook does
            8. When passing string literals to Android methods (Toast, TextView, Intent, etc.), always convert to Java String:
               - CORRECT: Java.use("java.lang.String").${'$'}new("message")
               - WRONG: "message" (JavaScript literal)
            9. When replacing void method implementations:
               - Do NOT call the original method (causes infinite recursion)
               - Do NOT use return statement
               - Simply implement the new behavior
            10. When replacing non-void method implementations and you need to call the original:
                - Use this.methodName.call(this, args) or store original reference before hooking
            11. Avoid using Java.scheduleOnMainThread() unless strictly necessary
                - Methods called from UI events (button clicks, etc.) are already on main thread
            12. When hooking Android framework interfaces, hook the concrete implementation class instead:
                - SharedPreferences${'$'}Editor → android.app.SharedPreferencesImpl${'$'}EditorImpl
                - ContentResolver → android.content.ContentResolver (concrete class)
                - Abstract/interface methods cannot be hooked directly
                - Common implementations:
                  * SharedPreferences${'$'}Editor.putString() → android.app.SharedPreferencesImpl${'$'}EditorImpl.putString()
                  * SharedPreferences${'$'}Editor.commit() → android.app.SharedPreferencesImpl${'$'}EditorImpl.commit()
                  * SharedPreferences${'$'}Editor.apply() → android.app.SharedPreferencesImpl${'$'}EditorImpl.apply()
            13. Avoid overload any method from java api. Is too easy to make the application crash when done.
            14. When asked to bypass password replace true stick to boolean methods
            
            OUTPUT:
            Provide ONLY the JavaScript code, no explanations.
        """.trimIndent()
    }

    /**
     * Descreve a ação requerida
     */
    private fun getActionDescription(action: ActionType, returnValue: String?): String {
        return when (action) {
            ActionType.RETURN_FALSE ->
                returnValue ?: "Always return false (bypass the check)"

            ActionType.RETURN_TRUE ->
                returnValue ?: "Always return true"

            ActionType.RETURN_NULL ->
                "Always return null"

            ActionType.RETURN_CUSTOM ->
                "Return the value: $returnValue"

            ActionType.LOG_CALLS ->
                returnValue ?: "Log all method calls with parameters and return values"

            ActionType.MODIFY_PARAMS ->
                "Intercept and modify method parameters"

            ActionType.HOOK_GENERIC ->
                returnValue ?: "Hook the method and log when it's called"
        }
    }
}
