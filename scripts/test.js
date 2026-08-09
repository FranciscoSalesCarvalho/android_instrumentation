// Frida script to capture sensitive logs being logged in DIVA app
Java.perform(function() {
    console.log("[*] Starting sensitive log capture...");

    // Hook android.util.Log methods to capture all log output
    var Log = Java.use("android.util.Log");

    // Hook Log.v (verbose)
    Log.v.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
        if (tag.indexOf("diva") !== -1 || tag.indexOf("DIVA") !== -1 || 
            tag.indexOf("jakhar") !== -1 || tag.indexOf("Jakhar") !== -1) {
            console.log("[Log.v] Tag: " + tag + " | Message: " + msg);
        }
        return this.v(tag, msg);
    };

    // Hook Log.d (debug)
    Log.d.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
        console.log("[Log.d] Tag: " + tag + " | Message: " + msg);
        return this.d(tag, msg);
    };

    // Hook Log.i (info)
    Log.i.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
        console.log("[Log.i] Tag: " + tag + " | Message: " + msg);
        return this.i(tag, msg);
    };

    // Hook Log.w (warning)
    Log.w.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
        console.log("[Log.w] Tag: " + tag + " | Message: " + msg);
        return this.w(tag, msg);
    };

    // Hook Log.e (error)
    Log.e.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
        console.log("[Log.e] Tag: " + tag + " | Message: " + msg);
        return this.e(tag, msg);
    };

    // Hook Log.wtf (what a terrible failure)
    try {
        Log.wtf.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
            console.log("[Log.wtf] Tag: " + tag + " | Message: " + msg);
            return this.wtf(tag, msg);
        };
    } catch(e) {
        console.log("[!] Could not hook Log.wtf: " + e);
    }

    // Hook LogActivity.processCC - this is the main vulnerable method that logs credit card info
    try {
        var LogActivity = Java.use("jakhar.aseem.diva.LogActivity");
        LogActivity.processCC.implementation = function(ccNum) {
            console.log("[*] LogActivity.processCC called with credit card number: " + ccNum);
            this.processCC(ccNum);
        };
        console.log("[+] Hooked LogActivity.processCC");
    } catch(e) {
        console.log("[!] Could not hook LogActivity.processCC: " + e);
    }

    // Hook LogActivity.checkout to capture when checkout is triggered
    try {
        var LogActivity2 = Java.use("jakhar.aseem.diva.LogActivity");
        LogActivity2.checkout.implementation = function(view) {
            console.log("[*] LogActivity.checkout called");
            this.checkout(view);
        };
        console.log("[+] Hooked LogActivity.checkout");
    } catch(e) {
        console.log("[!] Could not hook LogActivity.checkout: " + e);
    }

    // Hook InsecureDataStorage1Activity.saveCredentials - credentials stored in SharedPreferences
    try {
        var IDS1 = Java.use("jakhar.aseem.diva.InsecureDataStorage1Activity");
        IDS1.saveCredentials.implementation = function(view) {
            console.log("[*] InsecureDataStorage1Activity.saveCredentials called");
            this.saveCredentials(view);
        };
        console.log("[+] Hooked InsecureDataStorage1Activity.saveCredentials");
    } catch(e) {
        console.log("[!] Could not hook InsecureDataStorage1Activity.saveCredentials: " + e);
    }

    // Hook InsecureDataStorage2Activity.saveCredentials - credentials stored in SQLite
    try {
        var IDS2 = Java.use("jakhar.aseem.diva.InsecureDataStorage2Activity");
        IDS2.saveCredentials.implementation = function(view) {
            console.log("[*] InsecureDataStorage2Activity.saveCredentials called");
            this.saveCredentials(view);
        };
        console.log("[+] Hooked InsecureDataStorage2Activity.saveCredentials");
    } catch(e) {
        console.log("[!] Could not hook InsecureDataStorage2Activity.saveCredentials: " + e);
    }

    // Hook SharedPreferences$EditorImpl.putString to capture credentials being saved
    try {
        var EditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
        EditorImpl.putString.implementation = function(key, value) {
            // Filter for DIVA-related preferences
            console.log("[SharedPreferences.putString] Key: " + key + " | Value: " + value);
            return this.putString(key, value);
        };
        console.log("[+] Hooked SharedPreferencesImpl$EditorImpl.putString");
    } catch(e) {
        console.log("[!] Could not hook SharedPreferencesImpl$EditorImpl.putString: " + e);
    }

    // Hook HardcodeActivity.access to see hardcoded credentials being checked
    try {
        var HardcodeActivity = Java.use("jakhar.aseem.diva.HardcodeActivity");
        HardcodeActivity.access.implementation = function(view) {
            console.log("[*] HardcodeActivity.access called - checking hardcoded credentials");
            this.access(view);
        };
        console.log("[+] Hooked HardcodeActivity.access");
    } catch(e) {
        console.log("[!] Could not hook HardcodeActivity.access: " + e);
    }

    // Hook Hardcode2Activity.access
    try {
        var Hardcode2Activity = Java.use("jakhar.aseem.diva.Hardcode2Activity");
        Hardcode2Activity.access.implementation = function(view) {
            console.log("[*] Hardcode2Activity.access called - checking hardcoded credentials");
            this.access(view);
        };
        console.log("[+] Hooked Hardcode2Activity.access");
    } catch(e) {
        console.log("[!] Could not hook Hardcode2Activity.access: " + e);
    }

    console.log("[*] All hooks installed. Monitoring for sensitive log data...");
});