/**
 * FridaForge - Coletor de Logs
 *
 * Intercepta android.util.Log (todos os níveis) e System.out.println
 * para capturar logs do aplicativo durante 30 segundos.
 *
 * Saída: JSON delimitado por UFAM
 */

'use strict';

Java.perform(function () {
    console.log("[+] Starting log interception...");

    var logs = [];

    // Hook android.util.Log (todos os níveis)
    var Log = Java.use("android.util.Log");

    // Verbose
    Log.v.overload('java.lang.String', 'java.lang.String').implementation = function (tag, msg) {
        logs.push({ level: "VERBOSE", tag: tag, message: msg, timestamp: Date.now() });
        return this.v(tag, msg);
    };

    // Debug
    Log.d.overload('java.lang.String', 'java.lang.String').implementation = function (tag, msg) {
        logs.push({ level: "DEBUG", tag: tag, message: msg, timestamp: Date.now() });
        return this.d(tag, msg);
    };

    // Info
    Log.i.overload('java.lang.String', 'java.lang.String').implementation = function (tag, msg) {
        logs.push({ level: "INFO", tag: tag, message: msg, timestamp: Date.now() });
        return this.i(tag, msg);
    };

    // Warning
    Log.w.overload('java.lang.String', 'java.lang.String').implementation = function (tag, msg) {
        logs.push({ level: "WARNING", tag: tag, message: msg, timestamp: Date.now() });
        return this.w(tag, msg);
    };

    // Error
    Log.e.overload('java.lang.String', 'java.lang.String').implementation = function (tag, msg) {
        logs.push({ level: "ERROR", tag: tag, message: msg, timestamp: Date.now() });
        return this.e(tag, msg);
    };

    // Hook System.out.println
    var PrintStream = Java.use("java.io.PrintStream");

    PrintStream.println.overload('java.lang.String').implementation = function (msg) {
        logs.push({ level: "PRINTLN", tag: "System.out", message: msg, timestamp: Date.now() });
        return this.println(msg);
    };

    console.log("[+] Log hooks installed");
    console.log("[+] Monitoring logs for 30 seconds...");

    // Após 30 segundos, output logs coletados
    setTimeout(function () {
        console.log("[+] Log collection complete");
        console.log("UFAM");
        console.log(JSON.stringify({ logs: logs }));
        console.log("UFAM");
    }, 30000);
});