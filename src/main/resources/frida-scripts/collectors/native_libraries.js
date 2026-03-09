'use strict';

(function () {

    const PROTECTION_PATTERNS = {
        'ROOT_DETECTION': ['root', 'su_check', 'checksu', 'detect_su', 'isrooted', 'superuser', 'magisk', 'busybox'],
        'FRIDA_DETECTION': ['frida', 'antifrida', 'gadget', 'injection'],
        'DEBUGGER_DETECTION': ['ptrace', 'anti_debug', 'antidebug', 'debugger', 'isdebugged', 'tracerpid'],
        'INTEGRITY_CHECK': ['integrity', 'checksum', 'tamper', 'crc_check', 'verify_apk', 'signature_check'],
        'EMULATOR_DETECTION': ['emulator', 'detect_emu', 'goldfish', 'qemu', 'genymotion'],
        'HOOK_DETECTION': ['hook_detect', 'antihook', 'check_hook', 'plt_check', 'got_check', 'check_maps']
    };

    var result = {
        arch: Process.arch,
        pointerSize: Process.pointerSize,
        modules: [],
        protections: [],
        summary: { total: 0, app: 0 }
    };

    var mods = Process.enumerateModules();
    result.summary.total = mods.length;

    for (var i = 0; i < mods.length; i++) {
        var m = mods[i];

        if (m.path.indexOf('/system/') === 0 ||
            m.path.indexOf('/vendor/') === 0 ||
            m.path.indexOf('/apex/') === 0) {
            continue;
        }

        // Só processar bibliotecas nativas (.so)
        if (m.name.indexOf('.so') === -1) {
            continue;
        }

        result.summary.app++;

        var exps = [];
        try {
            var ae = m.enumerateExports();
            for (var j = 0; j < Math.min(ae.length, 150); j++) {
                if (ae[j].type === 'function') {
                    exps.push(ae[j].name);

                    var expLower = ae[j].name.toLowerCase();
                    var cats = Object.keys(PROTECTION_PATTERNS);
                    for (var c = 0; c < cats.length; c++) {
                        var kws = PROTECTION_PATTERNS[cats[c]];
                        for (var k = 0; k < kws.length; k++) {
                            if (expLower.indexOf(kws[k]) !== -1) {
                                result.protections.push({
                                    category: cats[c],
                                    func: ae[j].name,
                                    module: m.name,
                                    address: ae[j].address.toString()
                                });
                                break;
                            }
                        }
                    }
                }
            }
        } catch (e) {}

        result.modules.push({
            name: m.name,
            path: m.path,
            size: m.size,
            exports: exps
        });
    }

    send(JSON.stringify(result));
})();