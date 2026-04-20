/**
 * FridaForge - Coletor de Informações do Manifest
 *
 * Coleta informações do AndroidManifest via PackageManager em runtime:
 * permissões, activities, services, receivers, intent filters, SDK versions
 *
 * Saída: JSON delimitado por UFAM
 */

'use strict';

Java.perform(function () {
    console.log("[+] Collecting manifest info...");

    try {
        var ActivityThread = Java.use("android.app.ActivityThread");
        var context = ActivityThread.currentApplication().getApplicationContext();
        var packageManager = context.getPackageManager();
        var packageName = context.getPackageName();

        var GET_ACTIVITIES = 0x00000001;
        var GET_SERVICES = 0x00000004;
        var GET_RECEIVERS = 0x00000002;
        var GET_PERMISSIONS = 0x00001000;

        var flags = GET_ACTIVITIES | GET_SERVICES | GET_RECEIVERS | GET_PERMISSIONS;
        var packageInfo = packageManager.getPackageInfo(packageName, flags);
        var appInfo = context.getApplicationInfo();

        // Permissões
        var permissions = [];
        if (packageInfo.requestedPermissions.value) {
            var perms = packageInfo.requestedPermissions.value;
            for (var i = 0; i < perms.length; i++) {
                permissions.push(perms[i].toString());
            }
        }

        // Helper: extrair intent filters de um componente
        function extractIntentFilters(componentInfo) {
            var intentFilters = [];

            if (componentInfo.intentFilters) {
                var filters = componentInfo.intentFilters.value;
                if (filters) {
                    for (var j = 0; j < filters.length; j++) {
                        var filter = filters[j];
                        var intentFilter = {
                            actions: [],
                            categories: [],
                            data: []
                        };

                        if (filter.actions) {
                            var actions = filter.actions.value;
                            if (actions) {
                                for (var k = 0; k < actions.length; k++) {
                                    if (actions[k]) {
                                        intentFilter.actions.push(actions[k].toString());
                                    }
                                }
                            }
                        }

                        if (filter.categories) {
                            var categories = filter.categories.value;
                            if (categories) {
                                for (var k = 0; k < categories.length; k++) {
                                    if (categories[k]) {
                                        intentFilter.categories.push(categories[k].toString());
                                    }
                                }
                            }
                        }

                        if (filter.dataSchemes) {
                            var schemes = filter.dataSchemes.value;
                            if (schemes) {
                                for (var k = 0; k < schemes.length; k++) {
                                    if (schemes[k]) {
                                        intentFilter.data.push({
                                            scheme: schemes[k].toString()
                                        });
                                    }
                                }
                            }
                        }

                        if (filter.mimeTypes) {
                            var mimeTypes = filter.mimeTypes.value;
                            if (mimeTypes) {
                                for (var k = 0; k < mimeTypes.length; k++) {
                                    if (mimeTypes[k]) {
                                        if (!intentFilter.data[k]) {
                                            intentFilter.data[k] = {};
                                        }
                                        intentFilter.data[k].mimeType = mimeTypes[k].toString();
                                    }
                                }
                            }
                        }

                        if (intentFilter.actions.length > 0 || intentFilter.categories.length > 0 || intentFilter.data.length > 0) {
                            intentFilters.push(intentFilter);
                        }
                    }
                }
            }

            return intentFilters;
        }

        // Activities
        var activities = [];
        if (packageInfo.activities.value) {
            var acts = packageInfo.activities.value;
            for (var i = 0; i < acts.length; i++) {
                var act = acts[i];
                activities.push({
                    name: act.name.value.toString(),
                    exported: act.exported.value,
                    intentFilters: extractIntentFilters(act)
                });
            }
        }

        // Services
        var services = [];
        if (packageInfo.services.value) {
            var srvs = packageInfo.services.value;
            for (var i = 0; i < srvs.length; i++) {
                var srv = srvs[i];
                services.push({
                    name: srv.name.value.toString(),
                    exported: srv.exported.value,
                    intentFilters: []
                });
            }
        }

        // Receivers
        var receivers = [];
        if (packageInfo.receivers.value) {
            var recs = packageInfo.receivers.value;
            for (var i = 0; i < recs.length; i++) {
                var rec = recs[i];
                receivers.push({
                    name: rec.name.value.toString(),
                    exported: rec.exported.value,
                    intentFilters: []
                });
            }
        }

        var result = {
            permissions: permissions,
            activities: activities,
            services: services,
            receivers: receivers,
            minSdk: 0,
            targetSdk: 0,
            isDebuggable: (appInfo.flags.value & 2) !== 0
        };

        try {
            if (appInfo.targetSdkVersion) {
                result.targetSdk = appInfo.targetSdkVersion.value;
            }
        } catch (e) {
            console.log("[-] targetSdk not available");
        }

        try {
            if (appInfo.minSdkVersion) {
                result.minSdk = appInfo.minSdkVersion.value;
            }
        } catch (e) {
            console.log("[-] minSdk not available");
        }

        console.log("[+] Manifest collected successfully");
        console.log("UFAM");
        console.log(JSON.stringify(result, null, 2));
        console.log("UFAM");
    } catch (e) {
        console.error("[-] Error collecting manifest: " + e.toString());
    }
});