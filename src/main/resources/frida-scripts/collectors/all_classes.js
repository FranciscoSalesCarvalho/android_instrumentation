/**
 * FridaForge - Coletor de Classes
 *
 * Enumera todas as classes carregadas no runtime via Java.enumerateLoadedClasses.
 *
 * Saída: JSON delimitado por UFAM
 */

'use strict';

Java.perform(function () {
    console.log("[+] Enumerating all classes...");

    var classes = [];

    Java.enumerateLoadedClasses({
        onMatch: function (className) {
            classes.push(className);
        },
        onComplete: function () {
            console.log("[+] Found " + classes.length + " classes");
            console.log("UFAM");
            console.log(JSON.stringify(classes));
            console.log("UFAM");
        }
    });
});