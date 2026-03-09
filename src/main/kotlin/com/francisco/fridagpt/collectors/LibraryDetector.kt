package com.francisco.fridagpt.collectors

import com.francisco.fridagpt.core.FridaConnector
import com.francisco.fridagpt.models.FrameworkInfo
import com.francisco.fridagpt.models.FrameworkType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Detecta frameworks e bibliotecas utilizadas pelo app
 */
class LibraryDetector(
    private val connector: FridaConnector
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Detecta frameworks conhecidos
     */
    suspend fun detect(): List<FrameworkInfo> {
        logger.info { "Detecting frameworks..." }

        val script = getDetectionScript()
        val rawOutput = connector.executeScript(script) ?: return emptyList()

        return try {
            val jsonStart = rawOutput.indexOf('[')
            val jsonEnd = rawOutput.lastIndexOf(']') + 1

            if (jsonStart == -1 || jsonEnd == 0) {
                logger.warn { "No JSON found in framework detection output" }
                return emptyList()
            }

            val jsonStr = rawOutput.substring(jsonStart, jsonEnd)
            val detected = json.decodeFromString<List<DetectedFramework>>(jsonStr)

            detected.map { fw ->
                FrameworkInfo(
                    name = fw.name,
                    version = fw.version,
                    type = parseFrameworkType(fw.type),
                    mainClasses = fw.classes
                )
            }

        } catch (e: Exception) {
            logger.error(e) { "Failed to parse frameworks: ${e.message}" }
            emptyList()
        }
    }

    private fun parseFrameworkType(type: String): FrameworkType {
        return when (type.uppercase()) {
            "NETWORKING" -> FrameworkType.NETWORKING
            "SERIALIZATION" -> FrameworkType.SERIALIZATION
            "DATABASE" -> FrameworkType.DATABASE
            "UI" -> FrameworkType.UI
            "SECURITY" -> FrameworkType.SECURITY
            else -> FrameworkType.OTHER
        }
    }

    private fun getDetectionScript(): String {
        return """
            Java.perform(function() {
                console.log("[+] Detecting frameworks...");
                
                var frameworks = [];
                
                // Lista de frameworks para detectar
                var checks = [
                    // Networking
                    {name: "OkHttp", class: "okhttp3.OkHttpClient", type: "NETWORKING"},
                    {name: "Retrofit", class: "retrofit2.Retrofit", type: "NETWORKING"},
                    {name: "Volley", class: "com.android.volley.RequestQueue", type: "NETWORKING"},
                    
                    // Serialization
                    {name: "Gson", class: "com.google.gson.Gson", type: "SERIALIZATION"},
                    {name: "Jackson", class: "com.fasterxml.jackson.databind.ObjectMapper", type: "SERIALIZATION"},
                    {name: "Moshi", class: "com.squareup.moshi.Moshi", type: "SERIALIZATION"},
                    
                    // Database
                    {name: "Room", class: "androidx.room.RoomDatabase", type: "DATABASE"},
                    {name: "Realm", class: "io.realm.Realm", type: "DATABASE"},
                    
                    // UI
                    {name: "Glide", class: "com.bumptech.glide.Glide", type: "UI"},
                    {name: "Picasso", class: "com.squareup.picasso.Picasso", type: "UI"},
                    
                    // Security
                    {name: "Conscrypt", class: "org.conscrypt.Conscrypt", type: "SECURITY"},
                    {name: "BouncyCastle", class: "org.bouncycastle.jce.provider.BouncyCastleProvider", type: "SECURITY"}
                ];
                
                // Verifica cada framework
                checks.forEach(function(check) {
                    try {
                        var cls = Java.use(check.class);
                        if (cls) {
                            console.log("[+] Detected: " + check.name);
                            
                            var version = "unknown";
                            // Tentar obter versão (depende do framework)
                            try {
                                if (check.name === "OkHttp") {
                                    var Version = Java.use("okhttp3.internal.Version");
                                    version = Version.userAgent();
                                }
                            } catch(e) {}
                            
                            frameworks.push({
                                name: check.name,
                                version: version,
                                type: check.type,
                                classes: [check.class]
                            });
                        }
                    } catch(e) {
                        // Framework não encontrado
                    }
                });
                
                console.log("[+] Detected " + frameworks.length + " frameworks");
                console.log("UFAM");
                console.log(JSON.stringify(frameworks));
                console.log("UFAM");
            });
        """.trimIndent()
    }

    @Serializable
    private data class DetectedFramework(
        val name: String,
        val version: String,
        val type: String,
        val classes: List<String>
    )
}
