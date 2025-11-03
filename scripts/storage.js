Java.perform(function() {
    console.log("[+] Collecting storage information...");
    
    try {
        var context = Java.use("android.app.ActivityThread").currentApplication().getApplicationContext();
        var File = Java.use("java.io.File");
        
        var filesDir = context.getFilesDir().getAbsolutePath();
        var dataDir = context.getApplicationInfo().dataDir.value;
        console.log("[+] Data dir: " + dataDir);
        
        // ========== DATABASES ==========
        console.log("[+] Scanning databases...");
        var databases = [];
        var dbDir = File.$new(dataDir + "/databases");
        
        if (dbDir.exists()) {
            var dbFiles = dbDir.listFiles();
            if (dbFiles) {
                for (var i = 0; i < dbFiles.length; i++) {
                    var dbFile = dbFiles[i];
                    if (dbFile.isFile() && !dbFile.getName().endsWith("-journal")) {
                        var dbPath = dbFile.getAbsolutePath();
                        var tables = [];
                        
                        // Tentar ler tabelas do SQLite
                        try {
                            var SQLiteDatabase = Java.use("android.database.sqlite.SQLiteDatabase");
                            var db = SQLiteDatabase.openDatabase(
                                dbPath, 
                                null, 
                                SQLiteDatabase.OPEN_READONLY.value
                            );
                            
                            var cursor = db.rawQuery(
                                "SELECT name FROM sqlite_master WHERE type='table'", 
                                null
                            );
                            
                            while (cursor.moveToNext()) {
                                tables.push(cursor.getString(0));
                            }
                            cursor.close();
                            db.close();
                        } catch(e) {
                            console.log("[-] Could not read tables from " + dbFile.getName());
                        }
                        
                        databases.push({
                            name: dbFile.getName(),
                            path: dbPath,
                            tables: tables,
                            size: dbFile.length()
                        });
                    }
                }
            }
        }
        
        // ========== SHARED PREFERENCES ==========
        console.log("[+] Scanning SharedPreferences...");
        var sharedPrefs = [];
        var prefsDir = File.$new(dataDir + "/shared_prefs");
        
        if (prefsDir.exists()) {
            var prefsFiles = prefsDir.listFiles();
            if (prefsFiles) {
                for (var i = 0; i < prefsFiles.length; i++) {
                    var prefsFile = prefsFiles[i];
                    if (prefsFile.isFile() && prefsFile.getName().endsWith(".xml")) {
                        var prefsName = prefsFile.getName().replace(".xml", "");
                        var keys = [];
                        
                        // Ler keys do SharedPreferences
                        try {
                            var prefs = context.getSharedPreferences(prefsName, 0);
                            var allEntries = prefs.getAll();
                            var keySet = allEntries.keySet();
                            var iterator = keySet.iterator();
                            
                            while (iterator.hasNext()) {
                                var key = iterator.next();
                                keys.push(key.toString());
                            }
                        } catch(e) {
                            console.log("[-] Could not read keys from " + prefsName);
                        }
                        
                        sharedPrefs.push({
                            name: prefsName,
                            path: prefsFile.getAbsolutePath(),
                            keys: keys
                        });
                    }
                }
            }
        }
        
        // ========== INTERNAL FILES ==========
        console.log("[+] Scanning internal files...");
        var internalFiles = [];
        var filesDirObj = File.$new(filesDir);
        
        if (filesDirObj.exists()) {
            var files = filesDirObj.listFiles();
            if (files) {
                for (var i = 0; i < files.length; i++) {
                    var file = files[i];
                    internalFiles.push({
                        name: file.getName(),
                        path: file.getAbsolutePath(),
                        size: file.length(),
                        isDirectory: file.isDirectory()
                    });
                }
            }
        }
        
        // ========== EXTERNAL STORAGE ==========
        console.log("[+] Checking external storage...");
        var externalFiles = [];
        
        try {
            var externalDir = context.getExternalFilesDir(null);
            if (externalDir && externalDir.exists()) {
                var extFiles = externalDir.listFiles();
                if (extFiles) {
                    for (var i = 0; i < extFiles.length; i++) {
                        var file = extFiles[i];
                        externalFiles.push({
                            name: file.getName(),
                            path: file.getAbsolutePath(),
                            size: file.length()
                        });
                    }
                }
            }
        } catch(e) {
            console.log("[-] No external storage access");
        }
        
        // ========== RESULT ==========
        var result = {
            databases: databases,
            sharedPreferences: sharedPrefs,
            filesDir: filesDir,
            internalFiles: internalFiles,
            externalFiles: externalFiles
        };
        
        console.log("[+] Storage scan completed");
        console.log("UFAM");
        console.log(JSON.stringify(result, null, 2));
        console.log("UFAM");
        
    } catch(e) {
        console.error("[-] Error collecting storage: " + e.toString());
        console.error("Stack: " + e.stack);
    }
});