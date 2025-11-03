package com.francisco.fridagpt.core

import com.francisco.fridagpt.collectors.AppInfoCollector
import com.francisco.fridagpt.collectors.ClassCollector
import com.francisco.fridagpt.collectors.FrameworkDetector
import com.francisco.fridagpt.collectors.ManifestCollector
import com.francisco.fridagpt.collectors.StorageCollector
import com.francisco.fridagpt.models.AppContext
import com.francisco.fridagpt.models.ClassCategory
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

class ContextCollector(private val connector: FridaConnector) {
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
     * Estatísticas do contexto coletado
     */
    fun printStats(context: AppContext) {
        logger.info { "=== Context Statistics ===" }
        logger.info { "Package: ${context.appInfo.packageName}" }
        logger.info { "Classes: ${context.classes.size}" }
        logger.info { "  - App: ${context.classes.count { it.category == ClassCategory.APP }}" }
        logger.info { "  - Library: ${context.classes.count { it.category == ClassCategory.LIBRARY }}" }
        logger.info { "  - Android: ${context.classes.count { it.category == ClassCategory.ANDROID }}" }
        logger.info { "=========================" }
    }
}
