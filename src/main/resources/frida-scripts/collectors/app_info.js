/**
 * FridaForge - Coletor de Informações do Aplicativo
 *
 * Coleta informações básicas do app via ActivityThread:
 * packageName, targetSdk, minSdk, isDebuggable, allowBackup, dataDir
 *
 * Saída: JSON delimitado por [APP_INFO]
 */

'use strict';

Java.perform(function () {
    try {
        var context = Java.use("android.app.ActivityThread")
            .currentApplication()
            .getApplicationContext();

        var appInfo = context.getApplicationInfo();
        var flags = appInfo.flags.value;

        var result = {
            packageName: context.getPackageName(),
            targetSdk: appInfo.targetSdkVersion.value,
            minSdk: appInfo.minSdkVersion.value,
            isDebuggable: (flags & 2) !== 0,
            allowBackup: (flags & 0x8000) !== 0,
            dataDir: appInfo.dataDir.value
        };

        console.log('UFAM');
        console.log(JSON.stringify(result, null, 2));
        console.log('UFAM');
    } catch (e) {
        console.error('Error: ' + e.message);
    }
});