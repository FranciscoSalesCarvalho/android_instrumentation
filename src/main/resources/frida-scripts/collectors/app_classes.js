/**
 * FridaForge - Coletor de Classes do Aplicativo
 *
 * Enumera classes carregadas filtrando pelo packageName do app.
 * Utiliza ActivityThread para obter o packageName dinamicamente.
 *
 * Saída: JSON delimitado por UFAM
 */

'use strict';

Java.perform(function () {
    console.log("[+] Enumerating app classes...");

    var context = Java.use("android.app.ActivityThread")
        .currentApplication()
        .getApplicationContext();

    var packageName = context.getPackageName();

    var classes = [];

    Java.enumerateLoadedClasses({
        onMatch: function (className) {
            if (className.startsWith(packageName)) {
                classes.push(className);
            }
        },
        onComplete: function () {
            console.log("[+] Found " + classes.length + " app classes");
            console.log("UFAM");
            console.log(JSON.stringify(classes));
            console.log("UFAM");
        }
    });
});