Java.perform(function() {
    console.log("[+] SSL Pinning Bypass Script Loaded");
    console.log("[+] Target: owasp.sat.agoat");
    
    // Bypass 1: OkHttp CertificatePinner
    try {
        var CertificatePinner = Java.use("okhttp3.CertificatePinner");
        CertificatePinner.check.overload('java.lang.String', 'java.util.List').implementation = function(hostname, peerCertificates) {
            console.log("[+] OkHttp CertificatePinner.check bypassed for: " + hostname);
            return;
        };
        console.log("[+] OkHttp CertificatePinner bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 1 failed: " + e);
    }
    
    // Bypass 2: OkHttp3 CertificatePinner Builder
    try {
        var CertificatePinnerBuilder = Java.use("okhttp3.CertificatePinner$Builder");
        CertificatePinnerBuilder.add.overload('java.lang.String', '[Ljava.lang.String;').implementation = function(pattern, pins) {
            console.log("[+] OkHttp CertificatePinner$Builder.add bypassed for: " + pattern);
            return this;
        };
        console.log("[+] OkHttp CertificatePinner Builder bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 2 failed: " + e);
    }
    
    // Bypass 3: X509TrustManager
    try {
        var X509TrustManager = Java.use("javax.net.ssl.X509TrustManager");
        var SSLContext = Java.use("javax.net.ssl.SSLContext");
        var TrustManager = Java.registerClass({
            name: "com.sensepost.test.TrustManager",
            implements: [X509TrustManager],
            methods: {
                checkClientTrusted: function(chain, authType) {},
                checkServerTrusted: function(chain, authType) {},
                getAcceptedIssuers: function() {
                    return Java.array("java.security.cert.X509Certificate", []);
                }
            }
        });
        console.log("[+] X509TrustManager bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 3 failed: " + e);
    }
    
    // Bypass 4: SSLContext
    try {
        var SSLContext = Java.use("javax.net.ssl.SSLContext");
        SSLContext.init.implementation = function(keyManager, trustManager, secureRandom) {
            console.log("[+] SSLContext.init bypassed");
            var X509TrustManager = Java.use("javax.net.ssl.X509TrustManager");
            var EmptyTrustManager = Java.registerClass({
                name: "com.sensepost.test.EmptyTrustManager",
                implements: [X509TrustManager],
                methods: {
                    checkClientTrusted: function(chain, authType) {},
                    checkServerTrusted: function(chain, authType) {},
                    getAcceptedIssuers: function() {
                        return Java.array("java.security.cert.X509Certificate", []);
                    }
                }
            });
            var TrustManagerArray = Java.array("javax.net.ssl.TrustManager", [EmptyTrustManager.$new()]);
            this.init(keyManager, TrustManagerArray, secureRandom);
        };
        console.log("[+] SSLContext bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 4 failed: " + e);
    }
    
    // Bypass 5: OkHttpClient Builder
    try {
        var OkHttpClientBuilder = Java.use("okhttp3.OkHttpClient$Builder");
        OkHttpClientBuilder.certificatePinner.implementation = function(certificatePinner) {
            console.log("[+] OkHttpClient.Builder.certificatePinner bypassed");
            return this;
        };
        console.log("[+] OkHttpClient Builder bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 5 failed: " + e);
    }
    
    // Bypass 6: HostnameVerifier
    try {
        var HostnameVerifier = Java.use("javax.net.ssl.HostnameVerifier");
        HostnameVerifier.verify.overload('java.lang.String', 'javax.net.ssl.SSLSession').implementation = function(hostname, session) {
            console.log("[+] HostnameVerifier.verify bypassed for: " + hostname);
            return true;
        };
        console.log("[+] HostnameVerifier bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 6 failed: " + e);
    }
    
    // Bypass 7: TrustManagerFactory
    try {
        var TrustManagerFactory = Java.use("javax.net.ssl.TrustManagerFactory");
        TrustManagerFactory.getTrustManagers.implementation = function() {
            console.log("[+] TrustManagerFactory.getTrustManagers bypassed");
            var X509TrustManager = Java.use("javax.net.ssl.X509TrustManager");
            var EmptyTrustManager = Java.registerClass({
                name: "com.sensepost.test.TrustManagerFactory",
                implements: [X509TrustManager],
                methods: {
                    checkClientTrusted: function(chain, authType) {},
                    checkServerTrusted: function(chain, authType) {},
                    getAcceptedIssuers: function() {
                        return Java.array("java.security.cert.X509Certificate", []);
                    }
                }
            });
            return Java.array("javax.net.ssl.TrustManager", [EmptyTrustManager.$new()]);
        };
        console.log("[+] TrustManagerFactory bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 7 failed: " + e);
    }
    
    // Bypass 8: HttpsURLConnection
    try {
        var HttpsURLConnection = Java.use("javax.net.ssl.HttpsURLConnection");
        HttpsURLConnection.setDefaultHostnameVerifier.implementation = function(hostnameVerifier) {
            console.log("[+] HttpsURLConnection.setDefaultHostnameVerifier bypassed");
            return;
        };
        HttpsURLConnection.setSSLSocketFactory.implementation = function(socketFactory) {
            console.log("[+] HttpsURLConnection.setSSLSocketFactory bypassed");
            return;
        };
        HttpsURLConnection.setHostnameVerifier.implementation = function(hostnameVerifier) {
            console.log("[+] HttpsURLConnection.setHostnameVerifier bypassed");
            return;
        };
        console.log("[+] HttpsURLConnection bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 8 failed: " + e);
    }
    
    // Bypass 9: OkHttp3 Request Builder (additional coverage)
    try {
        var Request = Java.use("okhttp3.Request");
        var RequestBuilder = Java.use("okhttp3.Request$Builder");
        console.log("[+] OkHttp Request classes loaded for monitoring");
    } catch(e) {
        console.log("[-] Bypass 9 failed: " + e);
    }
    
    console.log("[+] SSL Bypass script loaded successfully");
});