package com.francisco.fridagpt.core

import com.francisco.fridagpt.collectors.AppInfoCollector
import com.francisco.fridagpt.collectors.ClassCollector
import com.francisco.fridagpt.collectors.LibraryDetector
import com.francisco.fridagpt.collectors.ManifestCollector
import com.francisco.fridagpt.collectors.NativeLibraryCollector
import com.francisco.fridagpt.collectors.StorageCollector
import com.francisco.fridagpt.models.AppContext
import com.francisco.fridagpt.models.ClassCategory
import com.francisco.fridagpt.models.ClassInfo
import com.francisco.fridagpt.models.MethodInfo
import com.francisco.fridagpt.models.ParameterInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import kotlin.collections.map

private val logger = KotlinLogging.logger {}
typealias ProgressWrapper = suspend (message: String, block: suspend () -> List<ClassInfo>) -> List<ClassInfo>

class ContextCollector(
    private val connector: FridaConnector
) {
    private val appInfoCollector = AppInfoCollector(connector)
    private val classCollector = ClassCollector(connector)
    private val libraryDetector = LibraryDetector(connector)
    private val manifestCollector = ManifestCollector(connector)
    private val storageCollector = StorageCollector(connector)
    private val nativeCollector = NativeLibraryCollector(connector)

    /**
     * Coleta contexto básico (apenas essencial - mais rápido)
     */
    suspend fun collectFullContext(): AppContext? = coroutineScope {
        try {
            logger.info { "Collecting basic context..." }

            val appInfo = appInfoCollector.collect()
            val classes = classCollector.collectAppClassesOnly()
            val manifest = manifestCollector.collect()
            val storage = storageCollector.collect()
            val libraries = libraryDetector.detect()
            val native = nativeCollector.collect()

            if (appInfo == null) {
                logger.error { "Failed to collect app info" }
                return@coroutineScope null
            }

            logger.info { "Basic context collected" }
            logger.info { "  - App classes: ${classes.size}" }

            AppContext(
                appInfo = appInfo,
                classes = classes,
                libraries = libraries,
                manifest = manifest,
                storage = storage,
                nativeContext = native,
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

        val output = connector.executeCollectorScript(script) ?: return emptyList()

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
     * Coleta contexto inteligente baseado em query.
     * Suporta múltiplos hooks.
     *
     * @param query            Query do usuário para identificar classes e métodos relevantes.
     * @param progressWrapper  Wrapper opcional para a etapa de coleta de métodos, permitindo
     *                         ao chamador envolver o trabalho em um spinner ou qualquer outro
     *                         mecanismo de feedback visual, sem acoplar o ContextCollector a
     *                         dependências de UI.
     */
    suspend fun collectForGeneric(
        query: String,
        progressWrapper: ProgressWrapper? = null
    ): AppContext? = coroutineScope {
        try {
            logger.info { "Collecting context for query: $query" }

            // 1. Coleta básica primeiro
            val basicContext = collectFullContext() ?: return@coroutineScope null

            // 2. Identifica classes relevantes
            val keywords = extractKeywords(query)
            val relevantClasses = basicContext.classes

            logger.info { "Found ${relevantClasses.size} relevant classes for query" }

            // 3. Detecta se precisa buscar múltiplos métodos relacionados
            val needsMultipleMethods = detectMultipleMethodsNeed(query)

            // 4. Coleta métodos das classes relevantes SOB DEMANDA
            val collectBlock: suspend () -> List<ClassInfo> = {
                if (needsMultipleMethods) {
                    collectRelatedMethods(relevantClasses, keywords)
                } else {
                    logger.info { "Collecting specific methods" }
                    relevantClasses.map { classInfo ->
                        val methods = collectMethodsForClass(classInfo.name)
                        classInfo.copy(methods = methods)
                    }
                }
            }

            val classesWithMethods = progressWrapper?.invoke(
                "🔍 Collecting methods from ${relevantClasses.size} classes...",
                collectBlock
            ) ?: collectBlock()

            // 5. Retorna contexto enriquecido
            AppContext(
                appInfo = basicContext.appInfo,
                classes = classesWithMethods,
                libraries = basicContext.libraries,
                manifest = basicContext.manifest,
                storage = basicContext.storage,
                nativeContext = basicContext.nativeContext,
            )

        } catch (e: Exception) {
            logger.error(e) { "Query-based context collection failed: ${e.message}" }
            null
        }
    }

    /**
     * Coleta contexto básico com métodos das classes relevantes.
     *
     * @param progressWrapper  Wrapper opcional para a etapa de coleta de métodos,
     *                         permitindo ao chamador exibir feedback visual sem
     *                         acoplar o ContextCollector a dependências de UI.
     */
    suspend fun collectBasicContext(
        progressWrapper: ProgressWrapper? = null
    ): AppContext? = coroutineScope {
        try {
            logger.info { "Collecting basic context..." }

            val appInfo = appInfoCollector.collect()
            if (appInfo == null) {
                logger.error { "Failed to collect app info" }
                return@coroutineScope null
            }

            // Coleta apenas classes do app (não todas)
            val classes = classCollector.collectAppClassesOnly().filter { classInfo ->
                !classInfo.name.contains("$")
            }
            val frameworks = libraryDetector.detect()

            val collectBlock: suspend () -> List<ClassInfo> = {
                classes.map { classInfo ->
                    val methods = collectMethodsForClass(classInfo.name)
                    classInfo.copy(methods = methods)
                }
            }

            val classesWithMethods = progressWrapper?.invoke(
                "🔍 Collecting methods from ${classes.size} classes...",
                collectBlock
            ) ?: collectBlock()

            AppContext(
                appInfo = appInfo,
                classes = classesWithMethods,
                libraries = frameworks,
                manifest = null,
                storage = null
            )

        } catch (e: Exception) {
            logger.error(e) { "Basic context collection failed: ${e.message}" }
            null
        }
    }

    /**
     * Extrai keywords relevantes da query
     */
    private fun extractKeywords(query: String): List<String> {
        val stopWords = setOf("hook", "bypass", "intercept", "return", "from", "the", "a", "an", "and", "or")

        return query.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 3 }
            .filterNot { it in stopWords }
            .distinct()
    }

    /**
     * Detecta se query precisa de múltiplos métodos
     */
    private fun detectMultipleMethodsNeed(query: String): Boolean {
        val lowerQuery = query.lowercase()

        // Keywords que sugerem múltiplos hooks
        val multiHookKeywords = listOf(
            "all", "any", "every", "bypass", "disable", "block",
            "emulator", "root", "ssl", "pinning", "detection",
            "log", "intercept", "monitor"
        )

        return multiHookKeywords.any { lowerQuery.contains(it) }
    }

    /**
     * Coleta métodos relacionados para cenários de múltiplos hooks
     */
    private suspend fun collectRelatedMethods(
        classes: List<ClassInfo>,
        keywords: List<String>
    ): List<ClassInfo> {
        return classes.map { classInfo ->
            val allMethods = collectMethodsForClass(classInfo.name)

            // Filtra apenas métodos relevantes baseado em keywords
            val relevantMethods = allMethods.filter { method ->
                keywords.any { keyword ->
                    method.name.contains(keyword, ignoreCase = true) ||
                            method.returnType.contains(keyword, ignoreCase = true)
                }
            }

            // Se não encontrou métodos específicos, mantém os principais
            val methodsToInclude = if (relevantMethods.isNotEmpty()) {
                relevantMethods
            } else {
                allMethods.take(10) // Top 10 métodos
            }

            classInfo.copy(methods = methodsToInclude)
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
        logger.info { "Libraries detected: ${context.libraries.size}" }
        context.libraries.forEach { fw ->
            logger.info { "  - ${fw.name} ${fw.version ?: ""} (${fw.type})" }
        }

        // Estatísticas de métodos
        val totalMethods = context.classes.sumOf { it.methods.size }
        if (totalMethods > 0) {
            logger.info { "Methods collected: $totalMethods" }
        }

        if (context.nativeContext != null) {
            val native = context.nativeContext!!
            logger.info { "Native modules: ${native.summary.app} app / ${native.summary.total} total" }
            logger.info { "  - Arch: ${native.arch} (${native.pointerSize * 8}-bit)" }
            native.modules.forEach { mod ->
                logger.info { "  - ${mod.name} (${mod.exports.size} exports)" }
            }
            if (native.protections.isNotEmpty()) {
                logger.info { "Native protections: ${native.protections.size}" }
                native.protections.forEach { p ->
                    logger.info { "  - [${p.category}] ${p.func} @ ${p.module}" }
                }
            }
        } else {
            logger.info { "Native modules: none detected" }
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
