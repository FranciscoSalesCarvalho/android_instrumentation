Java.perform(function() {
    console.log("[*] Starting WebView security monitoring...");
    
    try {
        // Hook WebView.loadUrl to detect insecure HTTP URLs
        var WebView = Java.use("android.webkit.WebView");
        
        WebView.loadUrl.overload('java.lang.String').implementation = function(url) {
            console.log("[WebView] Loading URL: " + url);
            
            if (url.startsWith("http://")) {
                console.log("[!] SECURITY WARNING: Insecure HTTP URL detected: " + url);
            } else if (url.startsWith("https://")) {
                console.log("[+] Secure HTTPS URL: " + url);
            } else if (url.startsWith("javascript:")) {
                console.log("[!] SECURITY WARNING: JavaScript URL scheme detected: " + url);
            } else if (url.startsWith("data:")) {
                console.log("[!] SECURITY WARNING: Data URL scheme detected: " + url);
            }
            
            return this.loadUrl(url);
        };
        
        // Hook WebView.loadUrl with headers
        WebView.loadUrl.overload('java.lang.String', 'java.util.Map').implementation = function(url, additionalHttpHeaders) {
            console.log("[WebView] Loading URL with headers: " + url);
            
            if (url.startsWith("http://")) {
                console.log("[!] SECURITY WARNING: Insecure HTTP URL detected: " + url);
            }
            
            if (additionalHttpHeaders) {
                var headersMap = Java.cast(additionalHttpHeaders, Java.use("java.util.HashMap"));
                var entrySet = headersMap.entrySet();
                var iterator = entrySet.iterator();
                console.log("[WebView] Additional headers:");
                while (iterator.hasNext()) {
                    var entry = iterator.next();
                    console.log("  " + entry.getKey() + ": " + entry.getValue());
                }
            }
            
            return this.loadUrl(url, additionalHttpHeaders);
        };
        
        // Hook WebView.loadData to detect insecure content
        WebView.loadData.implementation = function(data, mimeType, encoding) {
            console.log("[WebView] Loading data with MIME type: " + mimeType);
            
            if (data && data.length > 100) {
                console.log("[WebView] Data snippet: " + data.substring(0, 100) + "...");
            } else {
                console.log("[WebView] Data: " + data);
            }
            
            // Check for JavaScript in the data
            if (data && data.toLowerCase().includes("<script")) {
                console.log("[!] SECURITY WARNING: JavaScript detected in loaded data");
            }
            
            return this.loadData(data, mimeType, encoding);
        };
        
        // Hook WebView.loadDataWithBaseURL
        WebView.loadDataWithBaseURL.implementation = function(baseUrl, data, mimeType, encoding, historyUrl) {
            console.log("[WebView] Loading data with base URL: " + baseUrl);
            console.log("[WebView] History URL: " + historyUrl);
            
            if (baseUrl && baseUrl.startsWith("http://")) {
                console.log("[!] SECURITY WARNING: Insecure HTTP base URL: " + baseUrl);
            }
            
            if (data && data.toLowerCase().includes("<script")) {
                console.log("[!] SECURITY WARNING: JavaScript detected in loaded data");
            }
            
            return this.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
        };
        
    } catch (e) {
        console.log("[-] Error hooking WebView methods: " + e.toString());
    }
    
    try {
        // Hook WebSettings to monitor JavaScript enablement
        var WebSettings = Java.use("android.webkit.WebSettings");
        
        WebSettings.setJavaScriptEnabled.implementation = function(flag) {
            if (flag) {
                console.log("[!] SECURITY WARNING: JavaScript enabled in WebView");
            } else {
                console.log("[+] JavaScript disabled in WebView");
            }
            return this.setJavaScriptEnabled(flag);
        };
        
        WebSettings.setAllowFileAccess.implementation = function(allow) {
            if (allow) {
                console.log("[!] SECURITY WARNING: File access enabled in WebView");
            } else {
                console.log("[+] File access disabled in WebView");
            }
            return this.setAllowFileAccess(allow);
        };
        
        WebSettings.setAllowFileAccessFromFileURLs.implementation = function(flag) {
            if (flag) {
                console.log("[!] SECURITY WARNING: File access from file URLs enabled");
            } else {
                console.log("[+] File access from file URLs disabled");
            }
            return this.setAllowFileAccessFromFileURLs(flag);
        };
        
        WebSettings.setAllowUniversalAccessFromFileURLs.implementation = function(flag) {
            if (flag) {
                console.log("[!] SECURITY WARNING: Universal access from file URLs enabled");
            } else {
                console.log("[+] Universal access from file URLs disabled");
            }
            return this.setAllowUniversalAccessFromFileURLs(flag);
        };
        
        WebSettings.setMixedContentMode.implementation = function(mode) {
            console.log("[WebView] Mixed content mode set to: " + mode);
            if (mode == 2) { // MIXED_CONTENT_ALWAYS_ALLOW
                console.log("[!] SECURITY WARNING: Mixed content always allowed");
            }
            return this.setMixedContentMode(mode);
        };
        
    } catch (e) {
        console.log("[-] Error hooking WebSettings methods: " + e.toString());
    }
    
    try {
        // Hook WebViewClient methods to monitor resource loading
        var WebViewClient = Java.use("android.webkit.WebViewClient");
        
        WebViewClient.shouldOverrideUrlLoading.overload('android.webkit.WebView', 'java.lang.String').implementation = function(view, url) {
            console.log("[WebViewClient] Attempting to load URL: " + url);
            
            if (url.startsWith("http://")) {
                console.log("[!] SECURITY WARNING: Insecure HTTP URL in shouldOverrideUrlLoading: " + url);
            }
            
            return this.shouldOverrideUrlLoading(view, url);
        };
        
        WebViewClient.onReceivedError.overload('android.webkit.WebView', 'int', 'java.lang.String', 'java.lang.String').implementation = function(view, errorCode, description, failingUrl) {
            console.log("[WebViewClient] Error loading URL: " + failingUrl);
            console.log("[WebViewClient] Error code: " + errorCode + ", description: " + description);
            return this.onReceivedError(view, errorCode, description, failingUrl);
        };
        
    } catch (e) {
        console.log("[-] Error hooking WebViewClient methods: " + e.toString());
    }
    
    try {
        // Hook WebChromeClient to monitor JavaScript alerts and console messages
        var WebChromeClient = Java.use("android.webkit.WebChromeClient");
        
        WebChromeClient.onJsAlert.implementation = function(view, url, message, result) {
            console.log("[WebChromeClient] JavaScript Alert from " + url + ": " + message);
            return this.onJsAlert(view, url, message, result);
        };
        
        WebChromeClient.onConsoleMessage.overload('android.webkit.ConsoleMessage').implementation = function(consoleMessage) {
            var messageLevel = consoleMessage.messageLevel().toString();
            var message = consoleMessage.message();
            var sourceId = consoleMessage.sourceId();
            var lineNumber = consoleMessage.lineNumber();
            
            console.log("[WebChromeClient] Console " + messageLevel + " from " + sourceId + ":" + lineNumber + " - " + message);
            
            return this.onConsoleMessage(consoleMessage);
        };
        
    } catch (e) {
        console.log("[-] Error hooking WebChromeClient methods: " + e.toString());
    }
    
    console.log("[*] WebView security monitoring hooks installed successfully");
});