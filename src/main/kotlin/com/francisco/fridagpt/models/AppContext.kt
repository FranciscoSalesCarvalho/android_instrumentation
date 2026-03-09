package com.francisco.fridagpt.models

import kotlinx.serialization.Serializable

/**
 * Contexto completo do aplicativo Android coletado via Frida
 */
@Serializable
data class AppContext(
    val appInfo: AppInfo,
    val classes: List<ClassInfo>,
    val libraries: List<FrameworkInfo>,
    val manifest: ManifestInfo? = null,
    val storage: StorageInfo? = null,
    val nativeContext: NativeContext? = null
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

/**
* Informações de armazenamento
*/
@Serializable
data class StorageInfo(
    val databases: List<DatabaseInfo>,
    val sharedPreferences: List<SharedPreferencesInfo>,
    val filesDirectory: String,
    val internalFiles: List<FileInfo>,
    val externalFiles: List<FileInfo>,
    val vulnerabilities: List<StorageVulnerability>
)

@Serializable
data class DatabaseInfo(
    val name: String,
    val path: String,
    val tables: List<String> = emptyList()
)

@Serializable
data class SharedPreferencesInfo(
    val name: String,
    val path: String,
    val keys: List<String>,
    val hasSensitiveData: Boolean
)

@Serializable
data class FileInfo(
    val name: String,
    val path: String,
    val size: Long,
    val isDirectory: Boolean = false
)

/**
 * Vulnerabilidade de armazenamento detectada
 */
@Serializable
data class StorageVulnerability(
    val type: VulnerabilityType,
    val severity: Severity,
    val description: String,
    val details: String,
    val recommendation: String,
    val location: String
)

@Serializable
enum class VulnerabilityType {
    INSECURE_SHARED_PREFERENCES,
    UNENCRYPTED_DATABASE,
    EXTERNAL_STORAGE_USAGE,
    SUSPICIOUS_FILES,
    WORLD_READABLE_FILES,
    HARDCODED_SECRETS
}

@Serializable
enum class Severity {
    HIGH,
    MEDIUM,
    LOW,
    INFO
}
