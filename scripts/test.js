Java.perform(function() {
    console.log("[+] Starting SSL Certificate Validation Interception");
    
    // Hook the custom TrustManager in the app
    try {
        var MyTrustManager = Java.use("edu.ksu.cs.benign.MyTrustManager");
        
        MyTrustManager.checkServerTrusted.implementation = function(chain, authType) {
            console.log("[+] MyTrustManager.checkServerTrusted called");
            console.log("    Auth Type: " + authType);
            console.log("    Certificate Chain Length: " + chain.length);
            
            for (var i = 0; i < chain.length; i++) {
                console.log("    Certificate " + i + ":");
                console.log("      Subject: " + chain[i].getSubjectDN().toString());
                console.log("      Issuer: " + chain[i].getIssuerDN().toString());
                console.log("      Serial: " + chain[i].getSerialNumber().toString());
            }
            
            // Call original implementation to see if it throws or accepts
            try {
                var originalMethod = this.checkServerTrusted;
                originalMethod.call(this, chain, authType);
                console.log("[+] Custom TrustManager: Certificate validation PASSED");
            } catch (e) {
                console.log("[!] Custom TrustManager: Certificate validation FAILED - " + e.toString());
                console.log("[!] This indicates CERTIFICATE PINNING is implemented");
                throw e;
            }
        };
        
        MyTrustManager.checkClientTrusted.implementation = function(chain, authType) {
            console.log("[+] MyTrustManager.checkClientTrusted called");
            console.log("    Auth Type: " + authType);
            
            try {
                var originalMethod = this.checkClientTrusted;
                originalMethod.call(this, chain, authType);
                console.log("[+] Custom TrustManager: Client certificate validation PASSED");
            } catch (e) {
                console.log("[!] Custom TrustManager: Client certificate validation FAILED - " + e.toString());
                throw e;
            }
        };
        
        MyTrustManager.getAcceptedIssuers.implementation = function() {
            console.log("[+] MyTrustManager.getAcceptedIssuers called");
            var result = this.getAcceptedIssuers();
            console.log("    Accepted Issuers Count: " + result.length);
            return result;
        };
        
        console.log("[+] Hooked custom MyTrustManager");
    } catch (e) {
        console.log("[-] Failed to hook MyTrustManager: " + e.toString());
    }
    
    // Hook SSLContext initialization with correct overload
    try {
        var SSLContext = Java.use("javax.net.ssl.SSLContext");
        
        SSLContext.init.overload('[Ljavax.net.ssl.KeyManager;', '[Ljavax.net.ssl.TrustManager;', 'java.security.SecureRandom').implementation = function(keyManagers, trustManagers, secureRandom) {
            console.log("[+] SSLContext.init called");
            console.log("    Key Managers: " + (keyManagers ? keyManagers.length : "null"));
            console.log("    Trust Managers: " + (trustManagers ? trustManagers.length : "null"));
            
            if (trustManagers) {
                for (var i = 0; i < trustManagers.length; i++) {
                    console.log("    TrustManager " + i + ": " + trustManagers[i].getClass().getName());
                    if (trustManagers[i].getClass().getName() === "edu.ksu.cs.benign.MyTrustManager") {
                        console.log("[!] CUSTOM TRUST MANAGER DETECTED - This app implements certificate pinning");
                    }
                }
            }
            
            this.init(keyManagers, trustManagers, secureRandom);
        };
        
        console.log("[+] Hooked SSLContext.init");
    } catch (e) {
        console.log("[-] Failed to hook SSLContext: " + e.toString());
    }
    
    // Hook the anonymous HostnameVerifier from MyIntentService$1
    try {
        var AnonymousHostnameVerifier = Java.use("edu.ksu.cs.benign.MyIntentService$1");
        
        AnonymousHostnameVerifier.verify.implementation = function(hostname, session) {
            console.log("[+] Anonymous HostnameVerifier.verify called");
            console.log("    Hostname: " + hostname);
            console.log("    SSL Session: " + session.toString());
            
            var result = this.verify(hostname, session);
            console.log("    Verification result: " + result);
            if (result === false) {
                console.log("[!] Custom hostname verification FAILED - This may indicate additional SSL security");
            }
            return result;
        };
        
        console.log("[+] Hooked anonymous HostnameVerifier");
    } catch (e) {
        console.log("[-] Failed to hook anonymous HostnameVerifier: " + e.toString());
    }
    
    // Hook HttpsURLConnection to detect HTTPS connections
    try {
        var HttpsURLConnection = Java.use("javax.net.ssl.HttpsURLConnection");
        
        HttpsURLConnection.setHostnameVerifier.implementation = function(hostnameVerifier) {
            console.log("[+] HttpsURLConnection.setHostnameVerifier called");
            console.log("    Hostname Verifier: " + hostnameVerifier.getClass().getName());
            if (hostnameVerifier.getClass().getName().contains("MyIntentService")) {
                console.log("[!] CUSTOM HOSTNAME VERIFIER DETECTED - App implements custom SSL verification");
            }
            this.setHostnameVerifier(hostnameVerifier);
        };
        
        console.log("[+] Hooked HttpsURLConnection hostname verification");
    } catch (e) {
        console.log("[-] Failed to hook HttpsURLConnection: " + e.toString());
    }
    
    // Hook MyIntentService to monitor HTTPS requests
    try {
        var MyIntentService = Java.use("edu.ksu.cs.benign.MyIntentService");
        
        MyIntentService.onHandleIntent.implementation = function(intent) {
            console.log("[+] MyIntentService.onHandleIntent called");
            console.log("    Intent: " + intent.toString());
            console.log("[+] This service likely performs HTTPS requests with custom SSL validation");
            
            try {
                this.onHandleIntent(intent);
                console.log("[+] MyIntentService completed successfully");
            } catch (e) {
                console.log("[!] MyIntentService failed: " + e.toString());
                if (e.toString().contains("SSLContext") || e.toString().contains("certificate")) {
                    console.log("[!] SSL/Certificate related error - Certificate pinning may be enforced");
                }
                throw e;
            }
        };
        
        console.log("[+] Hooked MyIntentService.onHandleIntent");
    } catch (e) {
        console.log("[-] Failed to hook MyIntentService: " + e.toString());
    }
    
    // Monitor CertificateException for pinning detection
    try {
        var CertificateException = Java.use("java.security.cert.CertificateException");
        
        CertificateException.$init.overload('java.lang.String').implementation = function(message) {
            console.log("[!] CertificateException created with message: " + message);
            console.log("[!] CERTIFICATE PINNING ENFORCEMENT DETECTED");
            return this.$init(message);
        };
        
        console.log("[+] Hooked CertificateException constructor");
    } catch (e) {
        console.log("[-] Failed to hook CertificateException: " + e.toString());
    }
    
    console.log("[+] SSL Certificate Validation hooks installed");
    console.log("[+] Monitor for custom TrustManager and HostnameVerifier usage");
    console.log("[+] Certificate validation failures will indicate pinning implementation");
});