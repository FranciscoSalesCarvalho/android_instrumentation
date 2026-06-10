Java.perform(function() {
    console.log("[*] Starting authentication credential capture for com.vulnforum");
    
    try {
        // Hook SharedPreferences Editor implementations to capture stored credentials
        var SharedPrefsEditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
        SharedPrefsEditorImpl.putString.implementation = function(key, value) {
            try {
                var keyStr = key ? key.toString() : "null";
                var valueStr = value ? value.toString() : "null";
                
                // Look for common credential keys
                if (keyStr.toLowerCase().indexOf("password") !== -1 || 
                    keyStr.toLowerCase().indexOf("pass") !== -1 ||
                    keyStr.toLowerCase().indexOf("pwd") !== -1 ||
                    keyStr.toLowerCase().indexOf("token") !== -1 ||
                    keyStr.toLowerCase().indexOf("auth") !== -1 ||
                    keyStr.toLowerCase().indexOf("username") !== -1 ||
                    keyStr.toLowerCase().indexOf("user") !== -1 ||
                    keyStr.toLowerCase().indexOf("email") !== -1) {
                    console.log("[!] CREDENTIAL STORED - Key: " + keyStr + " | Value: " + valueStr);
                }
            } catch (e) {
                console.log("[-] Error in SharedPreferences hook: " + e);
            }
            return this.putString(key, value);
        };
        console.log("[+] Hooked SharedPreferences putString");
    } catch (e) {
        console.log("[-] Failed to hook SharedPreferences: " + e);
    }

    try {
        // Hook EditText to capture input field values
        var EditText = Java.use("android.widget.EditText");
        EditText.getText.implementation = function() {
            try {
                var result = this.getText();
                var text = result ? result.toString() : "";
                var hint = this.getHint();
                var hintStr = hint ? hint.toString() : "";
                
                // Check if this might be a password or username field
                if (text.length > 0 && (
                    hintStr.toLowerCase().indexOf("password") !== -1 ||
                    hintStr.toLowerCase().indexOf("username") !== -1 ||
                    hintStr.toLowerCase().indexOf("email") !== -1 ||
                    text.length >= 6)) { // Potential password length
                    console.log("[!] EDITTEXT INPUT - Hint: " + hintStr + " | Text: " + text);
                }
                return result;
            } catch (e) {
                console.log("[-] Error in EditText hook: " + e);
            }
            return this.getText();
        };
        console.log("[+] Hooked EditText getText");
    } catch (e) {
        console.log("[-] Failed to hook EditText: " + e);
    }

    try {
        // Hook HTTP request methods to capture credentials in network calls
        var HttpURLConnection = Java.use("java.net.HttpURLConnection");
        HttpURLConnection.getOutputStream.implementation = function() {
            try {
                console.log("[*] HTTP request detected - URL: " + this.getURL().toString());
                var originalStream = this.getOutputStream();
                
                // Create a wrapper to intercept written data
                var ByteArrayOutputStream = Java.use("java.io.ByteArrayOutputStream");
                var interceptStream = ByteArrayOutputStream.$new();
                
                return originalStream;
            } catch (e) {
                console.log("[-] Error in HttpURLConnection hook: " + e);
                return this.getOutputStream();
            }
        };
        console.log("[+] Hooked HttpURLConnection getOutputStream");
    } catch (e) {
        console.log("[-] Failed to hook HttpURLConnection: " + e);
    }

    try {
        // Hook common authentication method patterns
        Java.enumerateLoadedClasses({
            onMatch: function(className) {
                if (className.indexOf("com.vulnforum") === 0 && 
                    (className.toLowerCase().indexOf("login") !== -1 ||
                     className.toLowerCase().indexOf("auth") !== -1 ||
                     className.toLowerCase().indexOf("signin") !== -1)) {
                    
                    try {
                        var targetClass = Java.use(className);
                        var methods = targetClass.class.getDeclaredMethods();
                        
                        methods.forEach(function(method) {
                            var methodName = method.getName();
                            if (methodName.toLowerCase().indexOf("login") !== -1 ||
                                methodName.toLowerCase().indexOf("auth") !== -1 ||
                                methodName.toLowerCase().indexOf("signin") !== -1) {
                                
                                try {
                                    var originalMethod = targetClass[methodName];
                                    if (originalMethod) {
                                        targetClass[methodName].implementation = function() {
                                            console.log("[!] AUTH METHOD CALLED: " + className + "." + methodName);
                                            console.log("[!] Arguments: " + Array.prototype.slice.call(arguments).join(", "));
                                            return originalMethod.apply(this, arguments);
                                        };
                                        console.log("[+] Hooked " + className + "." + methodName);
                                    }
                                } catch (hookError) {
                                    // Skip method if hooking fails
                                }
                            }
                        });
                    } catch (classError) {
                        // Skip class if processing fails
                    }
                }
            },
            onComplete: function() {
                console.log("[*] Completed scanning for authentication classes");
            }
        });
    } catch (e) {
        console.log("[-] Failed to enumerate authentication classes: " + e);
    }

    try {
        // Hook Bundle to catch Intent data that might contain credentials
        var Bundle = Java.use("android.os.Bundle");
        Bundle.getString.overload('java.lang.String').implementation = function(key) {
            try {
                var result = this.getString(key);
                var keyStr = key ? key.toString() : "null";
                var valueStr = result ? result.toString() : "null";
                
                if (keyStr.toLowerCase().indexOf("password") !== -1 ||
                    keyStr.toLowerCase().indexOf("username") !== -1 ||
                    keyStr.toLowerCase().indexOf("email") !== -1 ||
                    keyStr.toLowerCase().indexOf("token") !== -1) {
                    console.log("[!] BUNDLE CREDENTIAL - Key: " + keyStr + " | Value: " + valueStr);
                }
                return result;
            } catch (e) {
                console.log("[-] Error in Bundle hook: " + e);
                return this.getString(key);
            }
        };
        console.log("[+] Hooked Bundle getString");
    } catch (e) {
        console.log("[-] Failed to hook Bundle: " + e);
    }

    console.log("[*] Authentication credential capture hooks installed successfully");
});