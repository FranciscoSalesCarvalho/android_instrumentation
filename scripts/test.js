Java.perform(function() {
    console.log("[+] Starting encryption interception script...");
    
    // Hook javax.crypto.Cipher
    try {
        var Cipher = Java.use("javax.crypto.Cipher");
        
        // Hook getInstance method to capture cipher algorithm and mode
        Cipher.getInstance.overload('java.lang.String').implementation = function(transformation) {
            console.log("[+] Cipher.getInstance() called");
            console.log("    Transformation: " + transformation);
            
            var result = this.getInstance(transformation);
            return result;
        };
        
        Cipher.getInstance.overload('java.lang.String', 'java.lang.String').implementation = function(transformation, provider) {
            console.log("[+] Cipher.getInstance() called with provider");
            console.log("    Transformation: " + transformation);
            console.log("    Provider: " + provider);
            
            var result = this.getInstance(transformation, provider);
            return result;
        };
        
        // Hook init methods to capture keys and parameters
        Cipher.init.overload('int', 'java.security.Key').implementation = function(opmode, key) {
            console.log("[+] Cipher.init() called");
            console.log("    Operation mode: " + opmode + " (" + (opmode == 1 ? "ENCRYPT" : opmode == 2 ? "DECRYPT" : "OTHER") + ")");
            console.log("    Algorithm: " + this.getAlgorithm());
            
            if (key) {
                try {
                    console.log("    Key algorithm: " + key.getAlgorithm());
                    console.log("    Key format: " + key.getFormat());
                    var keyBytes = key.getEncoded();
                    if (keyBytes) {
                        var keyHex = "";
                        for (var i = 0; i < keyBytes.length; i++) {
                            keyHex += ("0" + (keyBytes[i] & 0xFF).toString(16)).slice(-2);
                        }
                        console.log("    Key (hex): " + keyHex);
                    }
                } catch (e) {
                    console.log("    Key details unavailable: " + e);
                }
            }
            
            return this.init(opmode, key);
        };
        
        Cipher.init.overload('int', 'java.security.Key', 'java.security.spec.AlgorithmParameterSpec').implementation = function(opmode, key, params) {
            console.log("[+] Cipher.init() called with parameters");
            console.log("    Operation mode: " + opmode + " (" + (opmode == 1 ? "ENCRYPT" : opmode == 2 ? "DECRYPT" : "OTHER") + ")");
            console.log("    Algorithm: " + this.getAlgorithm());
            
            if (key) {
                try {
                    console.log("    Key algorithm: " + key.getAlgorithm());
                    var keyBytes = key.getEncoded();
                    if (keyBytes) {
                        var keyHex = "";
                        for (var i = 0; i < keyBytes.length; i++) {
                            keyHex += ("0" + (keyBytes[i] & 0xFF).toString(16)).slice(-2);
                        }
                        console.log("    Key (hex): " + keyHex);
                    }
                } catch (e) {
                    console.log("    Key details unavailable: " + e);
                }
            }
            
            if (params) {
                console.log("    Parameters: " + params.toString());
            }
            
            return this.init(opmode, key, params);
        };
        
        // Hook doFinal methods to capture input/output data
        Cipher.doFinal.overload('[B').implementation = function(input) {
            console.log("[+] Cipher.doFinal() called");
            console.log("    Algorithm: " + this.getAlgorithm());
            
            if (input) {
                var inputHex = "";
                for (var i = 0; i < input.length; i++) {
                    inputHex += ("0" + (input[i] & 0xFF).toString(16)).slice(-2);
                }
                console.log("    Input data (hex): " + inputHex);
                console.log("    Input data (string): " + Java.use("java.lang.String").$new(input));
            }
            
            var result = this.doFinal(input);
            
            if (result) {
                var outputHex = "";
                for (var i = 0; i < result.length; i++) {
                    outputHex += ("0" + (result[i] & 0xFF).toString(16)).slice(-2);
                }
                console.log("    Output data (hex): " + outputHex);
                try {
                    console.log("    Output data (string): " + Java.use("java.lang.String").$new(result));
                } catch (e) {
                    console.log("    Output data not printable as string");
                }
            }
            
            return result;
        };
        
        Cipher.doFinal.overload().implementation = function() {
            console.log("[+] Cipher.doFinal() called (no parameters)");
            console.log("    Algorithm: " + this.getAlgorithm());
            
            var result = this.doFinal();
            
            if (result) {
                var outputHex = "";
                for (var i = 0; i < result.length; i++) {
                    outputHex += ("0" + (result[i] & 0xFF).toString(16)).slice(-2);
                }
                console.log("    Output data (hex): " + outputHex);
            }
            
            return result;
        };
        
        // Hook update method to capture intermediate data
        Cipher.update.overload('[B').implementation = function(input) {
            console.log("[+] Cipher.update() called");
            console.log("    Algorithm: " + this.getAlgorithm());
            
            if (input) {
                var inputHex = "";
                for (var i = 0; i < input.length; i++) {
                    inputHex += ("0" + (input[i] & 0xFF).toString(16)).slice(-2);
                }
                console.log("    Update input (hex): " + inputHex);
            }
            
            var result = this.update(input);
            
            if (result) {
                var outputHex = "";
                for (var i = 0; i < result.length; i++) {
                    outputHex += ("0" + (result[i] & 0xFF).toString(16)).slice(-2);
                }
                console.log("    Update output (hex): " + outputHex);
            }
            
            return result;
        };
        
        console.log("[+] Successfully hooked javax.crypto.Cipher methods");
        
    } catch (e) {
        console.log("[-] Error hooking Cipher: " + e);
    }
    
    // Hook SecretKeySpec to capture key creation
    try {
        var SecretKeySpec = Java.use("javax.crypto.spec.SecretKeySpec");
        
        SecretKeySpec.$init.overload('[B', 'java.lang.String').implementation = function(key, algorithm) {
            console.log("[+] SecretKeySpec created");
            console.log("    Algorithm: " + algorithm);
            
            if (key) {
                var keyHex = "";
                for (var i = 0; i < key.length; i++) {
                    keyHex += ("0" + (key[i] & 0xFF).toString(16)).slice(-2);
                }
                console.log("    Key bytes (hex): " + keyHex);
            }
            
            return this.$init(key, algorithm);
        };
        
        console.log("[+] Successfully hooked SecretKeySpec");
        
    } catch (e) {
        console.log("[-] Error hooking SecretKeySpec: " + e);
    }
    
    // Hook IvParameterSpec to capture initialization vectors
    try {
        var IvParameterSpec = Java.use("javax.crypto.spec.IvParameterSpec");
        
        IvParameterSpec.$init.overload('[B').implementation = function(iv) {
            console.log("[+] IvParameterSpec created");
            
            if (iv) {
                var ivHex = "";
                for (var i = 0; i < iv.length; i++) {
                    ivHex += ("0" + (iv[i] & 0xFF).toString(16)).slice(-2);
                }
                console.log("    IV (hex): " + ivHex);
            }
            
            return this.$init(iv);
        };
        
        console.log("[+] Successfully hooked IvParameterSpec");
        
    } catch (e) {
        console.log("[-] Error hooking IvParameterSpec: " + e);
    }
    
    console.log("[+] Encryption interception script loaded successfully");
});