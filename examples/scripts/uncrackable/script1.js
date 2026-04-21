Java.perform(function() {
    console.log("[*] Starting cryptographic operations interception...");
    
    // Hook AES Cipher operations
    try {
        var Cipher = Java.use("javax.crypto.Cipher");
        
        Cipher.doFinal.overload('[B').implementation = function(input) {
            console.log("[*] Cipher.doFinal called");
            var result = this.doFinal(input);
            
            console.log("[*] Input bytes: " + bytesToHex(input));
            console.log("[*] Output bytes: " + bytesToHex(result));
            
            // Try to convert result to readable text
            try {
                var resultString = Java.use("java.lang.String").$new(result, "UTF-8");
                console.log("[*] Result as UTF-8: " + resultString);
            } catch (e) {
                console.log("[*] Could not convert result to UTF-8 string");
            }
            
            return result;
        };
        
        Cipher.doFinal.overload().implementation = function() {
            console.log("[*] Cipher.doFinal() called");
            var result = this.doFinal();
            
            console.log("[*] Output bytes: " + bytesToHex(result));
            
            // Try to convert result to readable text
            try {
                var resultString = Java.use("java.lang.String").$new(result, "UTF-8");
                console.log("[*] Result as UTF-8: " + resultString);
            } catch (e) {
                console.log("[*] Could not convert result to UTF-8 string");
            }
            
            return result;
        };
        
        console.log("[*] Cipher hooks installed successfully");
    } catch (e) {
        console.log("[!] Error hooking Cipher: " + e);
    }
    
    // Hook MessageDigest operations (hashing)
    try {
        var MessageDigest = Java.use("java.security.MessageDigest");
        
        MessageDigest.digest.overload('[B').implementation = function(input) {
            console.log("[*] MessageDigest.digest called");
            var result = this.digest(input);
            
            console.log("[*] Hash algorithm: " + this.getAlgorithm());
            console.log("[*] Input bytes: " + bytesToHex(input));
            
            // Try to convert input to readable text
            try {
                var inputString = Java.use("java.lang.String").$new(input, "UTF-8");
                console.log("[*] Input as UTF-8: " + inputString);
            } catch (e) {
                console.log("[*] Could not convert input to UTF-8 string");
            }
            
            console.log("[*] Hash result: " + bytesToHex(result));
            
            return result;
        };
        
        console.log("[*] MessageDigest hooks installed successfully");
    } catch (e) {
        console.log("[!] Error hooking MessageDigest: " + e);
    }
    
    // Hook Mac operations (HMAC)
    try {
        var Mac = Java.use("javax.crypto.Mac");
        
        Mac.doFinal.overload('[B').implementation = function(input) {
            console.log("[*] Mac.doFinal called");
            var result = this.doFinal(input);
            
            console.log("[*] MAC algorithm: " + this.getAlgorithm());
            console.log("[*] Input bytes: " + bytesToHex(input));
            
            // Try to convert input to readable text
            try {
                var inputString = Java.use("java.lang.String").$new(input, "UTF-8");
                console.log("[*] Input as UTF-8: " + inputString);
            } catch (e) {
                console.log("[*] Could not convert input to UTF-8 string");
            }
            
            console.log("[*] MAC result: " + bytesToHex(result));
            
            return result;
        };
        
        console.log("[*] Mac hooks installed successfully");
    } catch (e) {
        console.log("[!] Error hooking Mac: " + e);
    }
    
    // Hook KeyGenerator operations
    try {
        var KeyGenerator = Java.use("javax.crypto.KeyGenerator");
        
        KeyGenerator.generateKey.implementation = function() {
            console.log("[*] KeyGenerator.generateKey called");
            var key = this.generateKey();
            
            console.log("[*] Key algorithm: " + key.getAlgorithm());
            console.log("[*] Key format: " + key.getFormat());
            console.log("[*] Key bytes: " + bytesToHex(key.getEncoded()));
            
            return key;
        };
        
        console.log("[*] KeyGenerator hooks installed successfully");
    } catch (e) {
        console.log("[!] Error hooking KeyGenerator: " + e);
    }
    
    // Hook SecretKeySpec operations
    try {
        var SecretKeySpec = Java.use("javax.crypto.spec.SecretKeySpec");
        
        SecretKeySpec.$init.overload('[B', 'java.lang.String').implementation = function(key, algorithm) {
            console.log("[*] SecretKeySpec created");
            console.log("[*] Algorithm: " + algorithm);
            console.log("[*] Key bytes: " + bytesToHex(key));
            
            // Try to convert key to readable text
            try {
                var keyString = Java.use("java.lang.String").$new(key, "UTF-8");
                console.log("[*] Key as UTF-8: " + keyString);
            } catch (e) {
                console.log("[*] Could not convert key to UTF-8 string");
            }
            
            return this.$init(key, algorithm);
        };
        
        console.log("[*] SecretKeySpec hooks installed successfully");
    } catch (e) {
        console.log("[!] Error hooking SecretKeySpec: " + e);
    }
    
    // Hook Base64 encoding/decoding
    try {
        var Base64 = Java.use("android.util.Base64");
        
        Base64.encode.overload('[B', 'int').implementation = function(input, flags) {
            console.log("[*] Base64.encode called");
            var result = this.encode(input, flags);
            
            console.log("[*] Input bytes: " + bytesToHex(input));
            
            // Try to convert input to readable text
            try {
                var inputString = Java.use("java.lang.String").$new(input, "UTF-8");
                console.log("[*] Input as UTF-8: " + inputString);
            } catch (e) {
                console.log("[*] Could not convert input to UTF-8 string");
            }
            
            var resultString = Java.use("java.lang.String").$new(result, "UTF-8");
            console.log("[*] Base64 encoded: " + resultString);
            
            return result;
        };
        
        Base64.decode.overload('java.lang.String', 'int').implementation = function(str, flags) {
            console.log("[*] Base64.decode called");
            console.log("[*] Input string: " + str);
            
            var result = this.decode(str, flags);
            console.log("[*] Decoded bytes: " + bytesToHex(result));
            
            // Try to convert result to readable text
            try {
                var resultString = Java.use("java.lang.String").$new(result, "UTF-8");
                console.log("[*] Decoded as UTF-8: " + resultString);
            } catch (e) {
                console.log("[*] Could not convert decoded bytes to UTF-8 string");
            }
            
            return result;
        };
        
        console.log("[*] Base64 hooks installed successfully");
    } catch (e) {
        console.log("[!] Error hooking Base64: " + e);
    }
    
    // Utility function to convert bytes to hex
    function bytesToHex(bytes) {
        if (!bytes) return "null";
        var hex = "";
        for (var i = 0; i < bytes.length; i++) {
            var byte = (bytes[i] & 0xFF).toString(16);
            hex += (byte.length === 1 ? "0" + byte : byte);
        }
        return hex;
    }
    
    console.log("[*] All cryptographic hooks installed");
});