Java.perform(function() {
    console.log("[+] Starting JWT token interception script");
    
    // Hook common HTTP client libraries for JWT tokens in headers
    try {
        var OkHttpClient = Java.use("okhttp3.OkHttpClient");
        console.log("[+] Found OkHttpClient, hooking interceptors");
        
        var RealInterceptorChain = Java.use("okhttp3.internal.http.RealInterceptorChain");
        RealInterceptorChain.proceed.implementation = function(request) {
            var headers = request.headers();
            var headerNames = headers.names();
            var headerArray = headerNames.toArray();
            
            for (var i = 0; i < headerArray.length; i++) {
                var headerName = headerArray[i];
                if (headerName.toLowerCase().indexOf("authorization") !== -1 || 
                    headerName.toLowerCase().indexOf("token") !== -1 ||
                    headerName.toLowerCase().indexOf("auth") !== -1) {
                    var headerValue = headers.get(headerName);
                    console.log("[JWT] Header: " + headerName + " = " + headerValue);
                    
                    if (headerValue && (headerValue.indexOf("Bearer ") === 0 || headerValue.indexOf("JWT ") === 0)) {
                        console.log("[JWT] Token found: " + headerValue);
                    }
                }
            }
            
            return this.proceed(request);
        };
    } catch (e) {
        console.log("[-] OkHttpClient not found: " + e);
    }
    
    // Hook HttpURLConnection for JWT tokens
    try {
        var HttpURLConnection = Java.use("java.net.HttpURLConnection");
        HttpURLConnection.setRequestProperty.implementation = function(key, value) {
            if (key && value && 
                (key.toLowerCase().indexOf("authorization") !== -1 || 
                 key.toLowerCase().indexOf("token") !== -1 ||
                 key.toLowerCase().indexOf("auth") !== -1)) {
                console.log("[JWT] HttpURLConnection Header: " + key + " = " + value);
                
                if (value.indexOf("Bearer ") === 0 || value.indexOf("JWT ") === 0) {
                    console.log("[JWT] Token intercepted: " + value);
                }
            }
            return this.setRequestProperty(key, value);
        };
    } catch (e) {
        console.log("[-] HttpURLConnection not found: " + e);
    }
    
    // Hook SharedPreferences for stored JWT tokens
    try {
        var SharedPreferencesImpl = Java.use("android.app.SharedPreferencesImpl");
        SharedPreferencesImpl.getString.implementation = function(key, defValue) {
            var result = this.getString(key, defValue);
            if (key && result && 
                (key.toLowerCase().indexOf("token") !== -1 || 
                 key.toLowerCase().indexOf("jwt") !== -1 ||
                 key.toLowerCase().indexOf("auth") !== -1 ||
                 key.toLowerCase().indexOf("bearer") !== -1)) {
                console.log("[JWT] SharedPreferences - Key: " + key + " = " + result);
            }
            return result;
        };
        
        var SharedPreferencesEditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
        SharedPreferencesEditorImpl.putString.implementation = function(key, value) {
            if (key && value && 
                (key.toLowerCase().indexOf("token") !== -1 || 
                 key.toLowerCase().indexOf("jwt") !== -1 ||
                 key.toLowerCase().indexOf("auth") !== -1 ||
                 key.toLowerCase().indexOf("bearer") !== -1)) {
                console.log("[JWT] Storing token - Key: " + key + " = " + value);
            }
            return this.putString(key, value);
        };
    } catch (e) {
        console.log("[-] SharedPreferences hooks failed: " + e);
    }
    
    // Hook app-specific login method for JWT response
    try {
        var BankLogin = Java.use("com.app.damnvulnerablebank.BankLogin");
        BankLogin.bankLogin.implementation = function(view) {
            console.log("[+] BankLogin.bankLogin() called - monitoring for JWT response");
            this.bankLogin(view);
        };
    } catch (e) {
        console.log("[-] BankLogin hook failed: " + e);
    }
    
    // Hook JSON parsing for JWT tokens in response bodies
    try {
        var JSONObject = Java.use("org.json.JSONObject");
        JSONObject.getString.implementation = function(name) {
            var result = this.getString(name);
            if (name && result &&
                (name.toLowerCase().indexOf("token") !== -1 || 
                 name.toLowerCase().indexOf("jwt") !== -1 ||
                 name.toLowerCase().indexOf("auth") !== -1 ||
                 name.toLowerCase().indexOf("access") !== -1 ||
                 name.toLowerCase().indexOf("bearer") !== -1)) {
                console.log("[JWT] JSON Token - Key: " + name + " = " + result);
            }
            return result;
        };
    } catch (e) {
        console.log("[-] JSONObject hook failed: " + e);
    }
    
    // Hook Bundle for JWT tokens passed between activities
    try {
        var Bundle = Java.use("android.os.Bundle");
        Bundle.getString.overload('java.lang.String').implementation = function(key) {
            var result = this.getString(key);
            if (key && result &&
                (key.toLowerCase().indexOf("token") !== -1 || 
                 key.toLowerCase().indexOf("jwt") !== -1 ||
                 key.toLowerCase().indexOf("auth") !== -1)) {
                console.log("[JWT] Bundle Token - Key: " + key + " = " + result);
            }
            return result;
        };
        
        Bundle.putString.implementation = function(key, value) {
            if (key && value &&
                (key.toLowerCase().indexOf("token") !== -1 || 
                 key.toLowerCase().indexOf("jwt") !== -1 ||
                 key.toLowerCase().indexOf("auth") !== -1)) {
                console.log("[JWT] Bundle storing token - Key: " + key + " = " + value);
            }
            return this.putString(key, value);
        };
    } catch (e) {
        console.log("[-] Bundle hook failed: " + e);
    }
    
    // Hook Intent for JWT tokens in extras
    try {
        var Intent = Java.use("android.content.Intent");
        Intent.getStringExtra.implementation = function(name) {
            var result = this.getStringExtra(name);
            if (name && result &&
                (name.toLowerCase().indexOf("token") !== -1 || 
                 name.toLowerCase().indexOf("jwt") !== -1 ||
                 name.toLowerCase().indexOf("auth") !== -1)) {
                console.log("[JWT] Intent Token - Key: " + name + " = " + result);
            }
            return result;
        };
        
        Intent.putExtra.overload('java.lang.String', 'java.lang.String').implementation = function(name, value) {
            if (name && value &&
                (name.toLowerCase().indexOf("token") !== -1 || 
                 name.toLowerCase().indexOf("jwt") !== -1 ||
                 name.toLowerCase().indexOf("auth") !== -1)) {
                console.log("[JWT] Intent storing token - Key: " + name + " = " + value);
            }
            return this.putExtra(name, value);
        };
    } catch (e) {
        console.log("[-] Intent hook failed: " + e);
    }
    
    console.log("[+] JWT interception hooks installed");
});