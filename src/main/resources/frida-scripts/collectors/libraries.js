/**
 * FridaForge - Detector de Frameworks e Bibliotecas
 *
 * Detecta frameworks e bibliotecas de terceiros presentes no app
 * verificando a existência de classes-chave via Java.use.
 *
 * Categorias: NETWORKING, SERIALIZATION, DATABASE, UI, SECURITY
 *
 * Saída: JSON delimitado por UFAM
 */

'use strict';

Java.perform(function () {
    console.log("[+] Detecting libraries...");

    var frameworks = [];

    var checks = [
        // Networking
        { name: "OkHttp", class: "okhttp3.OkHttpClient", type: "NETWORKING" },
        { name: "Retrofit", class: "retrofit2.Retrofit", type: "NETWORKING" },
        { name: "Volley", class: "com.android.volley.RequestQueue", type: "NETWORKING" },

        // Serialization
        { name: "Gson", class: "com.google.gson.Gson", type: "SERIALIZATION" },
        { name: "Jackson", class: "com.fasterxml.jackson.databind.ObjectMapper", type: "SERIALIZATION" },
        { name: "Moshi", class: "com.squareup.moshi.Moshi", type: "SERIALIZATION" },

        // Database
        { name: "Room", class: "androidx.room.RoomDatabase", type: "DATABASE" },
        { name: "Realm", class: "io.realm.Realm", type: "DATABASE" },

        // UI
        { name: "Glide", class: "com.bumptech.glide.Glide", type: "UI" },
        { name: "Picasso", class: "com.squareup.picasso.Picasso", type: "UI" },

        // Security
        { name: "Conscrypt", class: "org.conscrypt.Conscrypt", type: "SECURITY" },
        { name: "BouncyCastle", class: "org.bouncycastle.jce.provider.BouncyCastleProvider", type: "SECURITY" }
    ];

    checks.forEach(function (check) {
        try {
            var cls = Java.use(check.class);
            if (cls) {
                console.log("[+] Detected: " + check.name);

                var version = "unknown";
                try {
                    if (check.name === "OkHttp") {
                        var Version = Java.use("okhttp3.internal.Version");
                        version = Version.userAgent();
                    }
                } catch (e) {}

                frameworks.push({
                    name: check.name,
                    version: version,
                    type: check.type,
                    classes: [check.class]
                });
            }
        } catch (e) {
            // Framework não encontrado
        }
    });

    console.log("[+] Detected " + frameworks.length + " frameworks");
    console.log("UFAM");
    console.log(JSON.stringify(frameworks));
    console.log("UFAM");
});