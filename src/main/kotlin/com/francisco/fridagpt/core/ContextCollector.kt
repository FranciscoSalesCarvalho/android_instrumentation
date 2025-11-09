package com.francisco.fridagpt.core

import com.francisco.fridagpt.collectors.AppInfoCollector
import com.francisco.fridagpt.collectors.ClassCollector
import com.francisco.fridagpt.collectors.FrameworkDetector
import com.francisco.fridagpt.collectors.ManifestCollector
import com.francisco.fridagpt.collectors.StorageCollector
import com.francisco.fridagpt.models.AppContext
import com.francisco.fridagpt.models.ClassCategory
import com.francisco.fridagpt.models.MethodInfo
import com.francisco.fridagpt.models.ParameterInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import kotlin.collections.map

private val logger = KotlinLogging.logger {}

class ContextCollector(
    private val connector: FridaConnector
) {
    private val appInfoCollector = AppInfoCollector(connector)
    private val classCollector = ClassCollector(connector)
    private val manifestCollector = ManifestCollector(connector)
    private val frameworkDetector = FrameworkDetector(connector)
    private val storageCollector = StorageCollector(connector)

    /**
     * Coleta contexto básico (apenas essencial - mais rápido)
     */
    suspend fun collectFullContext(): AppContext? = coroutineScope {
        try {
            logger.info { "Collecting basic context..." }

            val appInfo = appInfoCollector.collect()
            val classes = classCollector.collectAppClassesOnly()
            val manifest = manifestCollector.collect()
            val frameworks = frameworkDetector.detect()
            val storage = storageCollector.collect()

            if (appInfo == null) {
                logger.error { "Failed to collect app info" }
                return@coroutineScope null
            }

            logger.info { "Basic context collected" }
            logger.info { "  - App classes: ${classes.size}" }

            AppContext(
                appInfo = appInfo,
                classes = classes,
                frameworks = frameworks,
                manifest = manifest,
                storage = storage,
            )
        } catch (e: Exception) {
            logger.error(e) { "Basic context collection failed: ${e.message}" }
            null
        }
    }

    /**
     * Coleta métodos de uma classe específica SOB DEMANDA
     */
    suspend fun collectMethodsForClass(className: String): List<MethodInfo> {
        logger.info { "Collecting methods for class: $className" }

        val script = """
            Java.perform(function() {
                try {
                    var targetClass = Java.use("$className");
                    var methods = targetClass.class.getDeclaredMethods();
                    var methodList = [];
                    
                    for (var i = 0; i < methods.length; i++) {
                        var method = methods[i];
                        var methodName = method.getName();
                        var returnType = method.getReturnType().getName();
                        var params = method.getParameterTypes();
                        var paramTypes = [];
                        
                        for (var j = 0; j < params.length; j++) {
                            paramTypes.push(params[j].getName());
                        }
                        
                        methodList.push({
                            name: methodName,
                            returnType: returnType,
                            parameters: paramTypes,
                            signature: methodName + "(" + paramTypes.join(",") + "):" + returnType
                        });
                    }
                    
                    console.log("UFAM");
                    console.log(JSON.stringify(methodList));
                    console.log("UFAM");
                    
                } catch(e) {
                    console.error("Error collecting methods: " + e);
                }
            });
        """.trimIndent()

        val output = connector.executeScript(script) ?: return emptyList()

        return try {
            val jsonStart = output.indexOf('[')
            val jsonEnd = output.lastIndexOf(']') + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.warn { "No methods found for $className" }
                return emptyList()
            }

            val jsonStr = output.substring(jsonStart, jsonEnd)
            kotlinx.serialization.json.Json.decodeFromString<List<MethodInfoJson>>(jsonStr).map {
                MethodInfo(
                    name = it.name,
                    returnType = it.returnType,
                    parameters = it.parameters.map { param ->
                        ParameterInfo(type = param)
                    },
                    signature = it.signature
                )
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse methods for $className" }
            emptyList()
        }
    }

    /**
     * Estatísticas do contexto coletado
     */
    fun printStats(context: AppContext) {
        logger.info { "=== Context Statistics ===" }
        logger.info { "Package: ${context.appInfo.packageName}" }
        logger.info { "Classes: ${context.classes.size}" }
        logger.info { "  - App: ${context.classes.count { it.category == ClassCategory.APP }}" }
        logger.info { "  - Library: ${context.classes.count { it.category == ClassCategory.LIBRARY }}" }
        logger.info { "  - Android: ${context.classes.count { it.category == ClassCategory.ANDROID }}" }
        logger.info { "Frameworks detected: ${context.frameworks.size}" }
        context.frameworks.forEach { fw ->
            logger.info { "  - ${fw.name} ${fw.version ?: ""} (${fw.type})" }
        }

        // Estatísticas de métodos
        val totalMethods = context.classes.sumOf { it.methods.size }
        if (totalMethods > 0) {
            logger.info { "Methods collected: $totalMethods" }
        }

        logger.info { "=========================" }
    }
}

@Serializable
private data class MethodInfoJson(
    val name: String,
    val returnType: String,
    val parameters: List<String>,
    val signature: String
)
