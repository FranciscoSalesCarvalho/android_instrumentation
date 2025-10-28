package com.francisco.fridagpt.models

import kotlinx.serialization.Serializable

/**
 * Contexto completo do aplicativo Android coletado via Frida
 */
@Serializable
data class AppContext(
    val appInfo: AppInfo,
    val classes: List<ClassInfo>,
    val frameworks: List<FrameworkInfo>,
    val manifest: ManifestInfo? = null,
)

/**
 * Informações básicas do aplicativo
 */
@Serializable
data class AppInfo(
    val packageName: String,
    val targetSdk: Int,
    val minSdk: Int,
    val isDebuggable: Boolean,
    val backupActive: Boolean,
    val dataDir: String,
)

/**
 * Informações sobre uma classe Java/Kotlin
 */
@Serializable
data class ClassInfo(
    val name: String,
    val packageName: String,
    val methods: List<MethodInfo> = emptyList(),
    val category: ClassCategory = ClassCategory.APP
)

/**
 * Informações sobre um método
 */
@Serializable
data class MethodInfo(
    val name: String,
    val returnType: String,
    val parameters: List<ParameterInfo>,
    val isPublic: Boolean = true,
    val isStatic: Boolean = false,
    val signature: String
)

/**
 * Informação de parâmetro
 */
@Serializable
data class ParameterInfo(
    val type: String,
    val name: String? = null
)

/**
 * Categoria da classe
 */
@Serializable
enum class ClassCategory {
    APP,           // Classes do próprio app
    LIBRARY,       // Bibliotecas de terceiros
    ANDROID,       // Framework Android
    UNKNOWN
}

/**
 * Framework/biblioteca detectada
 */
@Serializable
data class FrameworkInfo(
    val name: String,
    val version: String? = null,
    val type: FrameworkType,
    val mainClasses: List<String> = emptyList()
)

@Serializable
enum class FrameworkType {
    NETWORKING,
    SERIALIZATION,
    DATABASE,
    UI,
    SECURITY,
    OTHER
}

/**
 * Informações do AndroidManifest
 */
@Serializable
data class ManifestInfo(
    val permissions: List<String>,
    val activities: List<ComponentInfo>,
    val services: List<ComponentInfo>,
    val receivers: List<ComponentInfo>,
    val minSdk: Int,
    val targetSdk: Int,
    val isDebuggable: Boolean
)

@Serializable
data class ComponentInfo(
    val name: String,
    val exported: Boolean,
    val intentFilters: List<IntentFilter> = emptyList()
)

@Serializable
data class IntentFilter(
    val actions: List<String>,
    val categories: List<String>,
    val data: List<String>,
)
