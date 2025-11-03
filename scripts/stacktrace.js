Java.perform(function() {
    
    // Hook em SSLPeerUnverifiedException para capturar o erro original
    try {
        var SSLPeerUnverifiedException = Java.use("javax.net.ssl.SSLPeerUnverifiedException");
        
        SSLPeerUnverifiedException.$init.overload('java.lang.String').implementation = function(message) {
            console.log("\n[EXCEPTION CAPTURED]");
            console.log("Type: SSLPeerUnverifiedException");
            console.log("Message: " + message);
            
            // Captura stack trace
            var JavaString = Java.use("java.lang.String");
            var JavaThrowable = Java.use("java.lang.Throwable");
            var exception = JavaThrowable.$new();
            var stackTrace = exception.getStackTrace();
            
            console.log("Stack trace:");
            for (var i = 0; i < stackTrace.length; i++) {
                console.log("  at " + stackTrace[i].toString());
            }
            console.log("");
            
            return this.$init(message);
        };
        
        console.log("[✓] Exception hook installed");
        
    } catch(e) {
        console.log("[✗] Exception hook failed: " + e);
    }
});