Java.perform(function() {
    console.log("[*] Starting sensitive data logging capture...");
    
    // Hook Android Log class to capture all log messages
    try {
        var Log = Java.use("android.util.Log");
        
        Log.d.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
            console.log("[LOG.D] Tag: " + tag + " | Message: " + msg);
            return this.d(tag, msg);
        };
        
        Log.i.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
            console.log("[LOG.I] Tag: " + tag + " | Message: " + msg);
            return this.i(tag, msg);
        };
        
        Log.e.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
            console.log("[LOG.E] Tag: " + tag + " | Message: " + msg);
            return this.e(tag, msg);
        };
        
        Log.w.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
            console.log("[LOG.W] Tag: " + tag + " | Message: " + msg);
            return this.w(tag, msg);
        };
        
        Log.v.overload('java.lang.String', 'java.lang.String').implementation = function(tag, msg) {
            console.log("[LOG.V] Tag: " + tag + " | Message: " + msg);
            return this.v(tag, msg);
        };
        
        console.log("[+] Android Log hooks installed");
    } catch (e) {
        console.log("[-] Error hooking Android Log: " + e);
    }
    
    // Hook System.out.println to capture console outputs
    try {
        var PrintStream = Java.use("java.io.PrintStream");
        PrintStream.println.overload('java.lang.String').implementation = function(msg) {
            console.log("[SYSTEM.OUT] " + msg);
            return this.println(msg);
        };
        console.log("[+] System.out.println hook installed");
    } catch (e) {
        console.log("[-] Error hooking System.out: " + e);
    }
    
    // Hook LogActivity.processCC method - likely processes credit card data
    try {
        var LogActivity = Java.use("jakhar.aseem.diva.LogActivity");
        LogActivity.processCC.implementation = function(ccNumber) {
            console.log("[SENSITIVE] Credit Card being processed: " + ccNumber);
            return this.processCC(ccNumber);
        };
        console.log("[+] LogActivity.processCC hook installed");
    } catch (e) {
        console.log("[-] Error hooking LogActivity.processCC: " + e);
    }
    
    // Hook all saveCredentials methods to capture credential storage
    var activities = [
        "jakhar.aseem.diva.InsecureDataStorage1Activity",
        "jakhar.aseem.diva.InsecureDataStorage2Activity", 
        "jakhar.aseem.diva.InsecureDataStorage3Activity",
        "jakhar.aseem.diva.InsecureDataStorage4Activity"
    ];
    
    activities.forEach(function(activityName) {
        try {
            var Activity = Java.use(activityName);
            Activity.saveCredentials.implementation = function(view) {
                console.log("[SENSITIVE] Credentials being saved in " + activityName);
                return this.saveCredentials(view);
            };
            console.log("[+] " + activityName + ".saveCredentials hook installed");
        } catch (e) {
            console.log("[-] Error hooking " + activityName + ": " + e);
        }
    });
    
    // Hook SharedPreferences Editor to capture stored data
    try {
        var EditorImpl = Java.use("android.app.SharedPreferencesImpl$EditorImpl");
        EditorImpl.putString.implementation = function(key, value) {
            console.log("[SENSITIVE] SharedPreferences putString - Key: " + key + " | Value: " + value);
            return this.putString(key, value);
        };
        console.log("[+] SharedPreferences putString hook installed");
    } catch (e) {
        console.log("[-] Error hooking SharedPreferences: " + e);
    }
    
    // Hook database operations that might log sensitive data
    try {
        var SQLiteDatabase = Java.use("android.database.sqlite.SQLiteDatabase");
        SQLiteDatabase.execSQL.overload('java.lang.String').implementation = function(sql) {
            console.log("[DATABASE] SQL executed: " + sql);
            return this.execSQL(sql);
        };
        console.log("[+] SQLiteDatabase.execSQL hook installed");
    } catch (e) {
        console.log("[-] Error hooking SQLiteDatabase: " + e);
    }
    
    // Hook file operations that might write sensitive data
    try {
        var FileOutputStream = Java.use("java.io.FileOutputStream");
        FileOutputStream.write.overload('[B').implementation = function(bytes) {
            var data = Java.use("java.lang.String").$new(bytes);
            console.log("[FILE] Data written to file: " + data);
            return this.write(bytes);
        };
        console.log("[+] FileOutputStream.write hook installed");
    } catch (e) {
        console.log("[-] Error hooking FileOutputStream: " + e);
    }
    
    console.log("[*] All logging hooks installed successfully");
});