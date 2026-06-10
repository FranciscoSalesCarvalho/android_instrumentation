Java.perform(function() {
    console.log("[+] Starting emulator detection bypass...");
    
    try {
        // Hook the main emulator detection method
        var EmulatorDetectionActivity = Java.use("owasp.sat.agoat.EmulatorDetectionActivity");
        EmulatorDetectionActivity.isEmulator.implementation = function() {
            console.log("[+] EmulatorDetectionActivity.isEmulator() called - bypassing");
            return false;
        };
        console.log("[+] Hooked EmulatorDetectionActivity.isEmulator()");
    } catch (e) {
        console.log("[-] Failed to hook EmulatorDetectionActivity.isEmulator(): " + e);
    }

    // Common Android emulator detection methods
    try {
        var Build = Java.use("android.os.Build");
        Build.FINGERPRINT.value = "google/sdk_gphone_x86/generic_x86:10/QSR1.190920.001/5891938:user/release-keys";
        Build.MODEL.value = "Pixel 3";
        Build.MANUFACTURER.value = "Google";
        Build.BRAND.value = "google";
        Build.DEVICE.value = "blueline";
        Build.PRODUCT.value = "blueline";
        Build.HARDWARE.value = "blueline";
        Build.ID.value = "QP1A.190711.020";
        Build.BOARD.value = "sdm845";
        console.log("[+] Modified Build properties");
    } catch (e) {
        console.log("[-] Failed to modify Build properties: " + e);
    }

    // Hook TelephonyManager for IMEI/device ID checks
    try {
        var TelephonyManager = Java.use("android.telephony.TelephonyManager");
        TelephonyManager.getDeviceId.overload().implementation = function() {
            console.log("[+] TelephonyManager.getDeviceId() called - returning fake IMEI");
            return "358240051111110";
        };
        
        TelephonyManager.getDeviceId.overload('int').implementation = function(slotIndex) {
            console.log("[+] TelephonyManager.getDeviceId(int) called - returning fake IMEI");
            return "358240051111110";
        };
        
        TelephonyManager.getSubscriberId.implementation = function() {
            console.log("[+] TelephonyManager.getSubscriberId() called - returning fake IMSI");
            return "310260000000000";
        };
        
        TelephonyManager.getLine1Number.implementation = function() {
            console.log("[+] TelephonyManager.getLine1Number() called - returning fake number");
            return "15551234567";
        };
        
        TelephonyManager.getNetworkOperatorName.implementation = function() {
            console.log("[+] TelephonyManager.getNetworkOperatorName() called");
            return "T-Mobile";
        };
        
        TelephonyManager.getSimOperatorName.implementation = function() {
            console.log("[+] TelephonyManager.getSimOperatorName() called");
            return "T-Mobile";
        };
        
        console.log("[+] Hooked TelephonyManager methods");
    } catch (e) {
        console.log("[-] Failed to hook TelephonyManager: " + e);
    }

    // Hook Settings.Secure for Android ID
    try {
        var Settings = Java.use("android.provider.Settings$Secure");
        Settings.getString.implementation = function(resolver, name) {
            if (name == "android_id") {
                console.log("[+] Settings.Secure.getString() called for android_id - returning fake ID");
                return "9774d56d682e549c";
            }
            return this.getString(resolver, name);
        };
        console.log("[+] Hooked Settings.Secure.getString()");
    } catch (e) {
        console.log("[-] Failed to hook Settings.Secure: " + e);
    }

    // Hook SystemProperties for emulator-specific properties
    try {
        var SystemProperties = Java.use("android.os.SystemProperties");
        SystemProperties.get.overload('java.lang.String').implementation = function(key) {
            var result = this.get(key);
            if (key.indexOf("ro.kernel.qemu") !== -1 || 
                key.indexOf("ro.bootmode") !== -1 ||
                key.indexOf("ro.hardware") !== -1) {
                console.log("[+] SystemProperties.get() called for " + key + " - original: " + result);
                if (key == "ro.hardware") {
                    return "qcom";
                }
                if (key == "ro.bootmode") {
                    return "unknown";
                }
                return "";
            }
            return result;
        };

        SystemProperties.get.overload('java.lang.String', 'java.lang.String').implementation = function(key, def) {
            var result = this.get(key, def);
            if (key.indexOf("ro.kernel.qemu") !== -1 || 
                key.indexOf("ro.bootmode") !== -1 ||
                key.indexOf("ro.hardware") !== -1) {
                console.log("[+] SystemProperties.get() called for " + key + " with default - original: " + result);
                if (key == "ro.hardware") {
                    return "qcom";
                }
                if (key == "ro.bootmode") {
                    return "unknown";
                }
                return def;
            }
            return result;
        };
        console.log("[+] Hooked SystemProperties.get()");
    } catch (e) {
        console.log("[-] Failed to hook SystemProperties: " + e);
    }

    // Hook File operations for common emulator detection files
    try {
        var File = Java.use("java.io.File");
        File.exists.implementation = function() {
            var name = this.getName();
            var path = this.getAbsolutePath();
            
            if (path.indexOf("goldfish") !== -1 || 
                path.indexOf("genymotion") !== -1 ||
                path.indexOf("andy") !== -1 ||
                path.indexOf("nox") !== -1 ||
                name == "qemu-props") {
                console.log("[+] File.exists() called for emulator file: " + path + " - returning false");
                return false;
            }
            return this.exists();
        };
        console.log("[+] Hooked File.exists()");
    } catch (e) {
        console.log("[-] Failed to hook File.exists(): " + e);
    }

    console.log("[+] Emulator detection bypass setup complete");
});