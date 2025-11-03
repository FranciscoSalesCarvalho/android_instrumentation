# Log Security Analysis - Detection Patterns

Comprehensive guide to sensitive data patterns detected by the Log Analyzer.

---

## 🚨 High Severity Patterns

These patterns indicate **critical security issues** that must be addressed immediately.

### 1. Passwords
**Keywords:** `password`, `passwd`, `pwd`, `senha`

**Example vulnerable logs:**
```
D/AuthManager: Login with password: myP@ssw0rd123
E/LoginActivity: Failed authentication for user admin with pwd=secret123
I/UserService: Updating password from oldpass to newpass
```

**Risk:** Direct credential exposure

**Fix:**
```kotlin
// ❌ BAD
Log.d("Auth", "Login with password: $password")

// ✅ GOOD
Log.d("Auth", "Login attempt for user: ${user.id}")
```

---

### 2. API Keys & Tokens
**Keywords:** `api_key`, `apikey`, `api-key`, `token`, `bearer`

**Example vulnerable logs:**
```
D/NetworkClient: API Key: sk-1234567890abcdef
I/AuthService: Bearer token: eyJhbGciOiJIUzI1NiIs...
D/Config: Initializing with apiKey=prod_key_abc123
```

**Risk:** Unauthorized API access

**Fix:**
```kotlin
// ❌ BAD
Log.d("API", "Request with key: $apiKey")

// ✅ GOOD
Log.d("API", "Authenticated request sent")
```

---

### 3. Secret Keys & Private Keys
**Keywords:** `secret`, `private_key`, `privatekey`

**Example vulnerable logs:**
```
D/Crypto: Private key: -----BEGIN RSA PRIVATE KEY-----
I/Config: Secret key for encryption: a1b2c3d4e5f6
```

**Risk:** Cryptographic compromise

---

### 4. JWT Tokens
**Pattern:** `eyJ[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+\.[A-Za-z0-9-_]+`

**Example vulnerable logs:**
```
D/Auth: JWT: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U
```

**Risk:** Session hijacking

---

### 5. Credit Card Numbers
**Pattern:** `\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b`

**Example vulnerable logs:**
```
D/Payment: Processing card 4532-1234-5678-9010
I/Checkout: Card ending in 9010, full: 4532123456789010
```

**Risk:** Financial fraud, PCI DSS violation

**Fix:** Never log full card numbers
```kotlin
// ❌ BAD
Log.d("Payment", "Card: $cardNumber")

// ✅ GOOD
Log.d("Payment", "Card ending: ${cardNumber.takeLast(4)}")
```

---

## ⚠️ Medium Severity Patterns

These patterns indicate **privacy concerns** that should be addressed.

### 6. Email Addresses
**Pattern:** Contains `@` or keywords `email`, `e-mail`

**Example vulnerable logs:**
```
D/UserProfile: Email updated to john.doe@example.com
I/Registration: New user: email=jane@domain.com
```

**Risk:** Privacy violation, spam target

---

### 7. Phone Numbers
**Pattern:** `\b\d{3}[-.]?\d{3}[-.]?\d{4}\b`

**Example vulnerable logs:**
```
D/Contact: Phone number: 555-123-4567
I/Profile: User phone: (555) 987-6543
```

**Risk:** Privacy violation, harassment

---

### 8. CPF (Brazilian ID)
**Pattern:** `\b\d{3}\.\d{3}\.\d{3}-\d{2}\b`

**Example vulnerable logs:**
```
D/Verification: CPF validated: 123.456.789-00
```

**Risk:** Identity theft

---

### 9. Session IDs
**Keywords:** `session_id`, `sessionid`, `jsessionid`

**Example vulnerable logs:**
```
D/Session: Created session: JSESSIONID=ABC123XYZ789
I/Auth: Session ID: sess_1234567890abcdef
```

**Risk:** Session hijacking

---

### 10. IMEI
**Pattern:** `\b\d{15}\b`

**Example vulnerable logs:**
```
D/DeviceInfo: IMEI: 123456789012345
```

**Risk:** Device tracking

---

### 11. MAC Address
**Pattern:** `([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})`

**Example vulnerable logs:**
```
D/Network: MAC Address: 00:1A:2B:3C:4D:5E
```

**Risk:** Device fingerprinting

---

## ℹ️ Low Severity Patterns

These patterns may indicate **potential information disclosure**.

### 12. GPS Coordinates
**Keywords:** `latitude`, `longitude`, `location`, `gps`

**Example vulnerable logs:**
```
D/LocationService: Lat: -23.5505, Lon: -46.6333
I/Maps: Current location: latitude=-23.5505, longitude=-46.6333
```

**Risk:** Location tracking

---

### 13. Addresses
**Keywords:** `address`, `endereco`, `rua`

**Risk:** Physical location disclosure

---

### 14. URLs
**Pattern:** `https?://[^\s]+`

**Example vulnerable logs:**
```
D/API: Calling https://api.internal.company.com/v1/users/123
```

**Risk:** Internal API exposure

---

### 15. File Paths
**Keywords:** `/data/data/`, `/sdcard/`, `file://`

**Example vulnerable logs:**
```
D/Storage: Saving to /data/data/com.app/files/sensitive.db
I/Cache: File at /sdcard/Download/document.pdf
```

**Risk:** Storage structure disclosure

---

### 16. SQL Queries
**Keywords:** `select`, `insert`, `update`, `delete`, `create table`

**Example vulnerable logs:**
```
D/Database: Executing: SELECT * FROM users WHERE password='abc123'
```

**Risk:** Database structure disclosure, SQL injection hints

---

## 🛡️ Best Practices

### 1. Use Conditional Logging

```kotlin
if (BuildConfig.DEBUG) {
    Log.d(TAG, "Debug info: $data")
}
```

### 2. Strip Logs in Release Builds

**ProGuard/R8 configuration:**
```proguard
# Remove all Log.* calls
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}
```

### 3. Custom Logger with Sanitization

```kotlin
object SecureLogger {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            val sanitized = sanitize(message)
            Log.d(tag, sanitized)
        }
    }
    
    private fun sanitize(message: String): String {
        return message
            .replace(Regex("""password[s]?\s*[=:]\s*\S+"""), "password=***")
            .replace(Regex("""token[s]?\s*[=:]\s*\S+"""), "token=***")
            .replace(Regex("""\b\d{4}[-\s]?\d{4}[-\s]?\d{4}[-\s]?\d{4}\b"""), "****-****-****-****")
    }
}
```

### 4. Use Timber with Custom Tree

```kotlin
class ReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= Log.WARN) {
            // Only log warnings and errors in production
            // Send to crash reporting (without sensitive data)
        }
    }
}

// In Application.onCreate()
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
} else {
    Timber.plant(ReleaseTree())
}
```

### 5. Never Log in Production

```kotlin
// ❌ BAD
Log.d("Payment", "Processing card: $cardNumber")

// ✅ GOOD - No logging at all for sensitive operations
// Use analytics/crash reporting instead (with proper sanitization)
```

---

## 🔍 How the Analyzer Works

The Log Analyzer uses **three detection methods**:

1. **Keyword Matching** - Searches for sensitive keywords in log messages
2. **Regex Pattern Matching** - Detects structured data (cards, emails, etc)
3. **Heuristic Analysis** - Identifies suspicious key=value pairs

### Detection Flow

```
Log Message
    ↓
Keyword Check (password, token, etc)
    ↓
Pattern Match (regex for cards, emails, etc)
    ↓
Heuristic Analysis (suspicious data structures)
    ↓
Severity Assignment (HIGH/MEDIUM/LOW)
    ↓
Generate Recommendation
```

---

## 📊 Example Analysis Output

```
╔═══════════════════════════════════════════════╗
║        Log Security Analysis Report          ║
╚═══════════════════════════════════════════════╝

📊 Statistics:
   Total logs analyzed: 234
   Sensitive logs found: 18
   └─ High severity: 4
   └─ Medium severity: 10
   └─ Low severity: 4

🚨 HIGH SEVERITY (4 logs):
   [DEBUG] AuthManager
   Message: User login successful with password: mySecretP@ss
   Patterns: Password
   🚨 CRITICAL: Remove logs containing Password. Use ProGuard/R8 to strip logs in release builds.

   [INFO] ApiClient
   Message: Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   Patterns: Auth Token, JWT Token
   🚨 CRITICAL: Remove logs containing Auth Token, JWT Token. Use ProGuard/R8 to strip logs in release builds.

💡 Recommendations:
   1. Remove all sensitive logging in production
   2. Use ProGuard/R8 to strip Log.* calls
   3. Implement custom logger with filtering
   4. Use BuildConfig.DEBUG checks
   5. Review and sanitize error messages
```

---

## 🎯 Integration in CI/CD

### Automated Testing

```bash
# In CI pipeline
./gradlew run --args="--package com.your.app --analyze-logs --log-duration 60"

# Check exit code
if grep -q "HIGH SEVERITY" log_analysis_report.txt; then
    echo "CRITICAL: High severity logs found!"
    exit 1
fi
```

### GitHub Actions Example

```yaml
name: Log Security Check

on: [pull_request]

jobs:
  log-analysis:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Setup Android
        uses: android-actions/setup-android@v2
      - name: Run App
        run: ./gradlew installDebug
      - name: Analyze Logs
        run: ./test_log_analysis.sh com.your.app
      - name: Check Results
        run: |
          if grep -q "HIGH SEVERITY" log_analysis_report.txt; then
            echo "::error::High severity logs detected"
            exit 1
          fi
```

---

## 📚 References

- [OWASP Mobile Top 10 - M2: Insecure Data Storage](https://owasp.org/www-project-mobile-top-10/)
- [Android Security Best Practices - Logging](https://developer.android.com/privacy-and-security/security-tips#log-data)
- [PCI DSS Requirements for Logging](https://www.pcisecuritystandards.org/)

---

**Last Updated:** 2025-01-06