Java.perform(function() {
    console.log("[+] Starting SSL/TLS certificate validation interception");
    
    // Hook the custom TrustManager implementation
    try {
        var MyTrustManager = Java.use("edu.ksu.cs.benign.MyTrustManager");
        
        MyTrustManager.checkServerTrusted.implementation = function(chain, authType) {
            console.log("[!] MyTrustManager.checkServerTrusted() called");
            console.log("[*] Certificate chain length: " + chain.length);
            console.log("[*] Auth type: " + authType);
            
            // Log certificate details
            for (var i = 0; i < chain.length; i++) {
                var cert = chain[i];
                console.log("[*] Certificate " + i + ":");
                console.log("    Subject: " + cert.getSubjectDN());
                console.log("    Issuer: " + cert.getIssuerDN());
                console.log("    Serial: " + cert.getSerialNumber());
            }
            
            // Check if the original method would throw an exception
            try {
                this.checkServerTrusted.call(this, chain, authType);
                console.log("[+] Certificate validation PASSED - App properly validates certificates");
            } catch (e) {
                console.log("[!] Certificate validation FAILED - App detected invalid certificate: " + e);
                throw e;
            }
        };
        
        MyTrustManager.getAcceptedIssuers.implementation = function() {
            var result = this.getAcceptedIssuers.call(this);
            console.log("[*] MyTrustManager.getAcceptedIssuers() called - returned " + (result ? result.length : 0) + " issuers");
            return result;
        };
        
        console.log("[+] Hooked MyTrustManager methods");
    } catch (e) {
        console.log("[!] Failed to hook MyTrustManager: " + e);
    }
    
    // Hook standard Android TrustManager implementations
    try {
        var X509TrustManager = Java.use("javax.net.ssl.X509TrustManager");
        var TrustManagerImpl = Java.use("com.android.org.conscrypt.TrustManagerImpl");
        
        TrustManagerImpl.checkServerTrusted.overload('[Ljava.security.cert.X509Certificate;', 'java.lang.String', 'java.lang.String').implementation = function(chain, authType, host) {
            console.log("[!] TrustManagerImpl.checkServerTrusted() called");
            console.log("[*] Host: " + host);
            console.log("[*] Auth type: " + authType);
            console.log("[*] Certificate chain length: " + chain.length);
            
            try {
                this.checkServerTrusted.overload('[Ljava.security.cert.X509Certificate;', 'java.lang.String', 'java.lang.String').call(this, chain, authType, host);
                console.log("[+] System certificate validation PASSED");
            } catch (e) {
                console.log("[!] System certificate validation FAILED: " + e);
                throw e;
            }
        };
        
        console.log("[+] Hooked TrustManagerImpl");
    } catch (e) {
        console.log("[!] Failed to hook TrustManagerImpl: " + e);
    }
    
    // Hook SSLContext initialization to detect trust manager usage
    try {
        var SSLContext = Java.use("javax.net.ssl.SSLContext");
        
        SSLContext.init.implementation = function(keyManagers, trustManagers, secureRandom) {
            console.log("[!] SSLContext.init() called");
            
            if (trustManagers != null) {
                console.log("[*] TrustManagers array length: " + trustManagers.length);
                for (var i = 0; i < trustManagers.length; i++) {
                    console.log("[*] TrustManager[" + i + "]: " + trustManagers[i].getClass().getName());
                }
            } else {
                console.log("[!] WARNING: No TrustManagers provided - using default!");
            }
            
            this.init.call(this, keyManagers, trustManagers, secureRandom);
        };
        
        console.log("[+] Hooked SSLContext.init()");
    } catch (e) {
        console.log("[!] Failed to hook SSLContext: " + e);
    }
    
    // Hook HttpsURLConnection to monitor certificate verification
    try {
        var HttpsURLConnection = Java.use("javax.net.ssl.HttpsURLConnection");
        
        HttpsURLConnection.setDefaultHostnameVerifier.implementation = function(hostnameVerifier) {
            console.log("[!] HttpsURLConnection.setDefaultHostnameVerifier() called");
            console.log("[*] HostnameVerifier: " + hostnameVerifier.getClass().getName());
            
            this.setDefaultHostnameVerifier.call(this, hostnameVerifier);
        };
        
        console.log("[+] Hooked HttpsURLConnection");
    } catch (e) {
        console.log("[!] Failed to hook HttpsURLConnection: " + e);
    }
    
    console.log("[+] SSL/TLS certificate validation hooks installed");
});