Java.perform(function() {
    try {
        // Hook the MainActivity class
        var MainActivity = Java.use("com.pentestmobile.appemulator.MainActivity");
        
        // Hook the isEmulator method
        MainActivity.isEmulator.implementation = function() {
            console.log("[+] Hook triggered: MainActivity.isEmulator() called");
            
            // Log method parameters (none in this case)
            console.log("[+] Method parameters: none");
            
            // Call the original method to see what it would return
            var originalResult = this.isEmulator();
            console.log("[+] Original method would return: " + originalResult);
            
            // Always return false to bypass the emulator check
            console.log("[+] Bypassing emulator check - returning false");
            return false;
        };
        
        console.log("[+] Successfully hooked MainActivity.isEmulator()");
        
    } catch (error) {
        console.log("[-] Error hooking MainActivity.isEmulator(): " + error.message);
        console.log("[-] Stack trace: " + error.stack);
    }
});