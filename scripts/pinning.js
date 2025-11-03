Java.perform(function() {
    console.log("[+] SSL Pinning Bypass Script Loaded");
    console.log("[+] Target: com.pentestmobile.appwebtest");
    
    // Bypass 1: OkHttp CertificatePinner
    try {
        var CertificatePinner = Java.use("okhttp3.CertificatePinner");
        CertificatePinner.check.overload('java.lang.String', 'java.util.List').implementation = function(hostname, peerCertificates) {
            console.log("[+] OkHttp CertificatePinner bypassed for: " + hostname);
            return;
        };
        console.log("[+] OkHttp CertificatePinner bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 1 failed: " + e);
    }
    
    // Bypass 2: TrustManager
    try {
        var X509TrustManager = Java.use('javax.net.ssl.X509TrustManager');
        var SSLContext = Java.use('javax.net.ssl.SSLContext');
        
        var TrustManager = Java.registerClass({
            name: 'com.sensepost.test.TrustManager',
            implements: [X509TrustManager],
            methods: {
                checkClientTrusted: function (chain, authType) {},
                checkServerTrusted: function (chain, authType) {},
                getAcceptedIssuers: function () {
                    return [];
                }
            }
        });
        
        var TrustManagers = [TrustManager.$new()];
        var SSLContextInstance = SSLContext.getInstance("TLS");
        SSLContextInstance.init(null, TrustManagers, null);
        SSLContext.getDefault = function () {
            return SSLContextInstance;
        };
        console.log("[+] Custom TrustManager bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 2 failed: " + e);
    }
    
    // Bypass 3: HttpsURLConnection
    try {
        var HttpsURLConnection = Java.use('javax.net.ssl.HttpsURLConnection');
        HttpsURLConnection.setDefaultHostnameVerifier.implementation = function(hostnameVerifier) {
            console.log("[+] HttpsURLConnection hostname verifier bypassed");
            return null;
        };
        
        var HostnameVerifier = Java.use('javax.net.ssl.HostnameVerifier');
        var TrustAllHostnameVerifier = Java.registerClass({
            name: 'com.sensepost.test.TrustAllHostnameVerifier',
            implements: [HostnameVerifier],
            methods: {
                verify: function (hostname, session) {
                    return true;
                }
            }
        });
        
        HttpsURLConnection.setDefaultHostnameVerifier(TrustAllHostnameVerifier.$new());
        console.log("[+] HttpsURLConnection bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 3 failed: " + e);
    }
    
    // Bypass 4: OkHttp HostnameVerifier
    try {
        var OkHostnameVerifier = Java.use('okhttp3.internal.tls.OkHostnameVerifier');
        OkHostnameVerifier.verify.overload('java.lang.String', 'javax.net.ssl.SSLSession').implementation = function(hostname, session) {
            console.log("[+] OkHttp HostnameVerifier bypassed for: " + hostname);
            return true;
        };
        console.log("[+] OkHttp HostnameVerifier bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 4 failed: " + e);
    }
    
    // Bypass 5: Socket factories
    try {
        var SSLSocketFactory = Java.use('javax.net.ssl.SSLSocketFactory');
        var X509TrustManager = Java.use('javax.net.ssl.X509TrustManager');
        
        var CustomTrustManager = Java.registerClass({
            name: 'com.sensepost.test.CustomTrustManager',
            implements: [X509TrustManager],
            methods: {
                checkClientTrusted: function (chain, authType) {},
                checkServerTrusted: function (chain, authType) {},
                getAcceptedIssuers: function () {
                    return Java.array('java.security.cert.X509Certificate', []);
                }
            }
        });
        
        var HttpsURLConnection = Java.use('javax.net.ssl.HttpsURLConnection');
        HttpsURLConnection.setDefaultSSLSocketFactory.implementation = function(sf) {
            console.log("[+] SSLSocketFactory bypassed");
            return null;
        };
        console.log("[+] Socket factory bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 5 failed: " + e);
    }
    
    // Bypass 6: NetworkSecurityPolicy
    try {
        var NetworkSecurityPolicy = Java.use('android.security.NetworkSecurityPolicy');
        NetworkSecurityPolicy.getInstance.implementation = function() {
            console.log("[+] NetworkSecurityPolicy bypassed");
            return Java.use('android.security.NetworkSecurityPolicy').$new();
        };
        
        if (NetworkSecurityPolicy.isCleartextTrafficPermitted) {
            NetworkSecurityPolicy.isCleartextTrafficPermitted.overload('java.lang.String').implementation = function(hostname) {
                console.log("[+] Cleartext traffic permitted for: " + hostname);
                return true;
            };
        }
        console.log("[+] NetworkSecurityPolicy bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 6 failed: " + e);
    }
    
    // Bypass 7: WebView SSL
    try {
        var WebViewClient = Java.use('android.webkit.WebViewClient');
        WebViewClient.onReceivedSslError.implementation = function(view, handler, error) {
            console.log("[+] WebView SSL error bypassed");
            handler.proceed();
        };
        
        WebViewClient.onReceivedError.implementation = function(view, request, error) {
            console.log("[+] WebView error bypassed");
        };
        console.log("[+] WebView SSL bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 7 failed: " + e);
    }
    
    // Bypass 8: Retrofit/OkHttp Builder
    try {
        var Builder = Java.use('okhttp3.OkHttpClient$Builder');
        Builder.certificatePinner.implementation = function(certificatePinner) {
            console.log("[+] OkHttpClient.Builder certificatePinner bypassed");
            return this;
        };
        
        Builder.hostnameVerifier.implementation = function(hostnameVerifier) {
            console.log("[+] OkHttpClient.Builder hostnameVerifier bypassed");
            return this;
        };
        console.log("[+] OkHttpClient.Builder bypass enabled");
    } catch(e) {
        console.log("[-] Bypass 8 failed: " + e);
    }
    
    console.log("[+] SSL Bypass script loaded successfully");
});