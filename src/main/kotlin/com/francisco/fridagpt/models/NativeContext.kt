package com.francisco.fridagpt.models

import kotlinx.serialization.Serializable

@Serializable
data class NativeContext(
    val arch: String,
    val pointerSize: Int,
    val modules: List<NativeModuleInfo>,
    val protections: List<ProtectionDetection>,
    val summary: NativeSummary
)

@Serializable
data class NativeModuleInfo(
    val name: String,
    val path: String,
    val size: Long,
    val exports: List<String>
)

@Serializable
data class ProtectionDetection(
    val category: String,
    val func: String,
    val module: String,
    val address: String
)

@Serializable
data class NativeSummary(
    val total: Int,
    val app: Int
)
