package com.francisco.fridagpt.core

import com.francisco.fridagpt.models.ClassInfo

/**
 * Matcher simples baseado em Jaccard Similarity com N-grams.
 * Encontra classes/métodos similares às keywords da query.
 */
class SimilarityMatcher(
    private val ngramSize: Int = 3,
    private val threshold: Double = 0.3
) {

    /**
     * Filtra List<ClassInfo> por similaridade com as keywords.
     * Considera nome da classe E nomes dos métodos.
     */
    fun filterClassInfos(
        classes: List<ClassInfo>,
        keywords: List<String>,
        limit: Int = 20
    ): List<ScoredClassInfo> {
        if (keywords.isEmpty()) return emptyList()

        return classes
            .map { classInfo ->
                val score = calculateClassInfoScore(classInfo, keywords)
                ScoredClassInfo(classInfo, score)
            }
            .filter { it.score >= threshold }
            .sortedByDescending { it.score }
            .take(limit)
    }

    /**
     * Calcula score para ClassInfo considerando classe + métodos
     */
    private fun calculateClassInfoScore(classInfo: ClassInfo, keywords: List<String>): Double {
        // Score do nome da classe
        val classScore = calculateSimilarity(classInfo.name, keywords)

        // Score dos métodos (pega o melhor match)
        val methodScore = classInfo.methods
            .maxOfOrNull { calculateSimilarity(it.name, keywords) } ?: 0.0

        // Retorna o maior entre classe e método
        // (classe com método relevante é tão boa quanto classe com nome relevante)
        return maxOf(classScore, methodScore)
    }

    /**
     * Calcula similaridade entre um nome e múltiplas keywords
     */
    fun calculateSimilarity(name: String, keywords: List<String>): Double {
        val nameLower = name.lowercase()
        val nameParts = splitCamelCase(name).map { it.lowercase() }
        val nameNgrams = nameParts.flatMap { generateNgrams(it) }.toSet()

        val scores = keywords.map { keyword ->
            val kwLower = keyword.lowercase()

            // 1. Match exato em alguma parte = score alto
            if (nameParts.any { it == kwLower }) return@map 1.0

            // 2. Keyword contida no nome = score bom
            if (nameLower.contains(kwLower)) return@map 0.8

            // 3. Alguma parte do nome contém keyword = score bom
            if (nameParts.any { it.contains(kwLower) }) return@map 0.7

            // 4. Fallback para Jaccard com n-grams
            val kwNgrams = generateNgrams(kwLower)
            jaccardSimilarity(nameNgrams, kwNgrams)
        }

        return scores.maxOrNull() ?: 0.0
    }

    /**
     * Jaccard Similarity: |A ∩ B| / |A ∪ B|
     */
    private fun jaccardSimilarity(set1: Set<String>, set2: Set<String>): Double {
        if (set1.isEmpty() && set2.isEmpty()) return 0.0
        val intersection = set1.intersect(set2).size
        val union = set1.union(set2).size
        return intersection.toDouble() / union.toDouble()
    }

    /**
     * Gera n-grams de uma string
     * Ex: "root" com n=3 -> ["roo", "oot"]
     */
    private fun generateNgrams(text: String): Set<String> {
        if (text.length < ngramSize) return setOf(text)
        return (0..text.length - ngramSize)
            .map { text.substring(it, it + ngramSize) }
            .toSet()
    }

    /**
     * Separa CamelCase em partes
     * Ex: "SecurityCheck" -> ["Security", "Check"]
     * Ex: "isRootDetected" -> ["is", "Root", "Detected"]
     */
    private fun splitCamelCase(text: String): List<String> {
        return text
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
            .split(" ", ".", "_")
            .filter { it.isNotBlank() }
    }
}

data class ScoredMatch(
    val name: String,
    val score: Double
)

data class ScoredClassInfo(
    val classInfo: ClassInfo,
    val score: Double
)