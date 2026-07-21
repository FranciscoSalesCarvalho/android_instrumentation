/**
 * Frida script to intercept authentication data storage in com.vulnforum
 * Hooks SessionManager and SharedPreferences to capture sensitive auth data
 */

Java.perform(function() {
    try {
        // =========================================================
        // Hook SessionManager - the primary auth data storage class
        // =========================================================
        var SessionManager = Java.use("com.vulnforum.SessionManager");

        // Hook saveSession to capture all stored auth data
        SessionManager.saveSession.implementation = function(token, username, role, balance) {
            console.log("\n[+] SessionManager.saveSession() called");
            console.log("    [TOKEN]    : " + token);
            console.log("    [USERNAME] : " + username);
            console.log("    [ROLE]     : " + role);
            console.log("    [BALANCE]  : " + balance);
            
            // Call the original method
            this.saveSession(token, username, role, balance);
        };

        // Hook getToken to see when/how the token is retrieved
        SessionManager.getToken.implementation = function() {
            var token = this.getToken();
            console.log("\n[+] SessionManager.getToken() called");
            console.log("    [TOKEN] : " + token);
            return token;
        };

        // Hook getUsername
        SessionManager.getUsername.implementation = function() {
            var username = this.getUsername();
            console.log("\n[+] SessionManager.getUsername() called");
            console.log("    [USERNAME] : " + username);
            return username;
        };

        // Hook getRole
        SessionManager.getRole.implementation = function() {
            var role = this.getRole();
            console.log("\n[+] SessionManager.getRole() called");
            console.log("    [ROLE] : " + role);
            return role;
        };

        // Hook getBalance
        SessionManager.getBalance.implementation = function() {
            var balance = this.getBalance();
            console.log("\n[+] SessionManager.getBalance() called");
            console.log("    [BALANCE] : " + balance);
            return balance;
        };

        // Hook clear to detect logout
        SessionManager.clear.implementation = function() {
            console.log("\n[+] SessionManager.clear() called - session being cleared");
            this.clear();
        };

        console.log("[*] SessionManager hooks installed successfully");

    } catch(e) {
        console.log("[-] Error hooking SessionManager: " + e);
    }

    try {
        // =========================================================
        // Hook SharedPreferencesImpl$EditorImpl for raw storage interception
        // =========================================================
        var EditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");

        EditorImpl.putString.implementation = function(key, value) {
            // Filter for auth-related keys
            var keyStr = key ? key.toString() : "null";
            var valueStr = value ? value.toString() : "null";
            
            // Only log potentially sensitive auth-related keys
            var sensitiveKeywords = ["token", "auth", "user", "pass", "session", "role", "balance", "credential", "key", "secret"];
            var isSensitive = false;
            
            for (var i = 0; i < sensitiveKeywords.length; i++) {
                if (keyStr.toLowerCase().indexOf(sensitiveKeywords[i]) !== -1) {
                    isSensitive = true;
                    break;
                }
            }
            
            if (isSensitive) {
                console.log("\n[+] SharedPreferences.putString() - SENSITIVE DATA DETECTED");
                console.log("    [KEY]   : " + keyStr);
                console.log("    [VALUE] : " + valueStr);
                // Print stack trace to identify caller
                var stack = Java.use("android.util.Log").getStackTraceString(
                    Java.use("java.lang.Exception").$new("Stack trace")
                );
                console.log("    [STACK] : " + stack.split("\n").slice(1, 6).join("\n            "));
            }
            
            return this.putString(key, value);
        };

        EditorImpl.putFloat.implementation = function(key, value) {
            var keyStr = key ? key.toString() : "null";
            var sensitiveKeywords = ["token", "auth", "user", "pass", "session", "role", "balance", "credential"];
            var isSensitive = false;
            
            for (var i = 0; i < sensitiveKeywords.length; i++) {
                if (keyStr.toLowerCase().indexOf(sensitiveKeywords[i]) !== -1) {
                    isSensitive = true;
                    break;
                }
            }
            
            if (isSensitive) {
                console.log("\n[+] SharedPreferences.putFloat() - SENSITIVE DATA DETECTED");
                console.log("    [KEY]   : " + keyStr);
                console.log("    [VALUE] : " + value);
            }
            
            return this.putFloat(key, value);
        };

        console.log("[*] SharedPreferencesImpl$EditorImpl hooks installed successfully");

    } catch(e) {
        console.log("[-] Error hooking SharedPreferencesImpl$EditorImpl: " + e);
    }

    try {
        // =========================================================
        // Hook LoginResponse to capture login data before it's stored
        // =========================================================
        var LoginResponse = Java.use("com.vulnforum.data.LoginResponse");

        LoginResponse.copy.implementation = function(token, username, role, balance) {
            console.log("\n[+] LoginResponse.copy() called");
            console.log("    [TOKEN]    : " + token);
            console.log("    [USERNAME] : " + username);
            console.log("    [ROLE]     : " + role);
            console.log("    [BALANCE]  : " + balance);
            return this.copy(token, username, role, balance);
        };

        console.log("[*] LoginResponse hooks installed successfully");

    } catch(e) {
        console.log("[-] Error hooking LoginResponse: " + e);
    }

    try {
        // =========================================================
        // Hook AuthService.login to capture login credentials
        // =========================================================
        var AuthService = Java.use("com.vulnforum.network.AuthService");

        // Note: login returns a Retrofit Call, hook at the interface level
        AuthService.login.implementation = function(loginRequest) {
            console.log("\n[+] AuthService.login() called");
            if (loginRequest) {
                console.log("    [LOGIN REQUEST] : " + loginRequest.toString());
            }
            return this.login(loginRequest);
        };

        console.log("[*] AuthService hooks installed successfully");

    } catch(e) {
        console.log("[-] Error hooking AuthService: " + e);
    }

    try {
        // =========================================================
        // Hook LoginRequest to capture credentials being sent
        // =========================================================
        var LoginRequest = Java.use("com.vulnforum.data.LoginRequest");

        LoginRequest.copy.implementation = function(username, password) {
            console.log("\n[+] LoginRequest.copy() called");
            console.log("    [USERNAME] : " + username);
            console.log("    [PASSWORD] : " + password);
            return this.copy(username, password);
        };

        console.log("[*] LoginRequest hooks installed successfully");

    } catch(e) {
        console.log("[-] Error hooking LoginRequest: " + e);
    }

    console.log("\n[*] All authentication storage hooks active. Waiting for events...\n");
});