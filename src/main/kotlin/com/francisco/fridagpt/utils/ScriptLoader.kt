package com.francisco.fridagpt.utils

object ScriptLoader {
    fun load(resourcePath: String): String {
        return this::class.java.classLoader
            ?.getResourceAsStream(resourcePath)
            ?.bufferedReader()?.use { it.readText() }
            ?: throw IllegalStateException("Script not found: $resourcePath")
    }
}