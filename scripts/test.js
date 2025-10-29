Java.perform(function() {
    console.log("[+] Starting multi-hook script");
    
    // Hook 1: com.francisco.appprotegido.MainActivity.isEmulator
    try {
        var MainActivity = Java.use("com.francisco.appprotegido.MainActivity");
        MainActivity.isEmulator.implementation = function() {
            console.log("[Hook 1] com.francisco.appprotegido.MainActivity.isEmulator called");
            console.log("[Hook 1] Bypassing emulator detection - returning false");
            return false;
        };
        console.log("[Hook 1] Successfully hooked isEmulator method");
    } catch(e) {
        console.error("[Hook 1] Failed: " + e);
    }
    
    // Hook 2: com.francisco.appprotegido.MainActivity.isRooted
    try {
        var MainActivity = Java.use("com.francisco.appprotegido.MainActivity");
        MainActivity.isRooted.implementation = function() {
            console.log("[Hook 2] com.francisco.appprotegido.MainActivity.isRooted called");
            console.log("[Hook 2] Bypassing root detection - returning false");
            return false;
        };
        console.log("[Hook 2] Successfully hooked isRooted method");
    } catch(e) {
        console.error("[Hook 2] Failed: " + e);
    }
    
    console.log("[+] All hooks installed");
});