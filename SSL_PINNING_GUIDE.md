# SSL Pinning Bypass Guide 🔐

## Overview

SSL Pinning é uma técnica de segurança onde o app valida o certificado do servidor contra um certificado "pinado" (hardcoded) no app, prevenindo ataques man-in-the-middle.

## Capabilities

### ✅ O Que Conseguimos Fazer:

1. **Bypass OkHttp SSL Pinning** (Mais comum)
2. **Bypass TrustManager customizado**
3. **Multiple hooks para bypass completo**
4. **Detecção automática de frameworks de rede**

### ⚠️ Limitações:

- Não detecta Network Security Config (XML)
- Requer conhecimento da implementação para bypasses específicos
- Apps com obfuscação pesada podem ser difíceis

## Quick Start

### 1. Detectar Se Tem SSL Pinning

```bash
# Coletar contexto
./gradlew run --args="-p TARGET_APP -c BASIC -o context.json"

# Verificar frameworks
cat context.json | jq '.frameworks'

# Procurar classes relacionadas
cat context.json | jq '.classes[].name' | grep -i "ssl\|certificate\|trust\|pin"
```

### 2. Bypass Genérico (Primeira Tentativa)

```bash
./gradlew run --args="-p TARGET_APP \
  -k YOUR_API_KEY \
  -q 'bypass ssl pinning'"
```

**O LLM vai:**
- Detectar OkHttp se presente
- Gerar hooks para CertificatePinner
- Tentar bypass de TrustManager
- Incluir fallbacks comuns

### 3. Bypass Específico (Se Souber a Implementação)

#### OkHttp (Mais Comum)

```bash
./gradlew run --args="-p TARGET_APP \
  -q 'hook okhttp3.CertificatePinner.check return null'"
```

#### TrustManager Customizado

```bash
./gradlew run --args="-p TARGET_APP \
  -q 'hook javax.net.ssl.X509TrustManager.checkServerTrusted return null'"
```

#### Multiple Hooks (Bypass Completo)

```bash
./gradlew run --args="-p TARGET_APP \
  -q 'hook okhttp3.CertificatePinner.check return null AND hook javax.net.ssl.X509TrustManager.checkServerTrusted return null AND hook com.android.org.conscrypt.TrustManagerImpl.verifyChain return null'"
```

## Common Implementations

### Implementation 1: OkHttp CertificatePinner

**Detection:**
- Framework: OkHttp detectado
- Classe: `okhttp3.CertificatePinner`

**Bypass:**
```bash
-q 'hook okhttp3.CertificatePinner.check return null'
```

**Expected Frida Script:**
```javascript
Java.perform(function() {
    var CertificatePinner = Java.use("okhttp3.CertificatePinner");
    CertificatePinner.check.overload('java.lang.String', 'java.util.List').implementation = function(hostname, peerCertificates) {
        console.log("[+] SSL Pinning bypassed for: " + hostname);
        return;
    };
});
```

### Implementation 2: Custom TrustManager

**Detection:**
- Classes com "TrustManager" no nome
- Implementa `X509TrustManager`

**Bypass:**
```bash
-q 'hook com.example.network.CustomTrustManager.checkServerTrusted return null'
```

### Implementation 3: Network Security Config

**Detection:**
- Arquivo `network_security_config.xml` no manifest
- Difícil de bypas

sar via Frida

**Workaround:**
```bash
# Hook no nível mais baixo
-q 'hook com.android.org.conscrypt.TrustManagerImpl.checkTrustedRecursive return null'
```

## Real-World Examples

### Example 1: Banking App (OkHttp)

```bash
# 1. Detectar
./gradlew run --args="-p com.bank.app -c BASIC"

# Output mostra: OkHttp 4.9.0 detected

# 2. Bypass
./gradlew run --args="-p com.bank.app \
  -q 'hook okhttp3.CertificatePinner.check return null'"
```

### Example 2: Social App (Custom Implementation)

```bash
# 1. Análise estática (JADX) revela:
#    - com.social.network.SSLPinningInterceptor
#    - com.social.security.CertValidator

# 2. Multi-hook bypass
./gradlew run --args="-p com.social.app \
  -q 'hook com.social.network.SSLPinningInterceptor.intercept return null AND hook com.social.security.CertValidator.validate return true'"
```

### Example 3: Enterprise App (Multiple Layers)

```bash
# Bypass em 3 camadas
./gradlew run --args="-p com.enterprise.app \
  -q 'hook okhttp3.CertificatePinner.check return null AND 
      hook javax.net.ssl.X509TrustManager.checkServerTrusted return null AND
      hook com.enterprise.security.PinningValidator.verify return true'"
```

## Testing Workflow

### Step 1: Information Gathering

```bash
# Run the test script
./test_ssl_pinning.sh com.target.app YOUR_API_KEY

# This will:
# - Detect frameworks
# - Find SSL-related classes
# - Generate 3 bypass scripts
```

### Step 2: Try Scripts in Order

```bash
# 1. Try generic first (fastest)
frida -U -f com.target.app -l generic_ssl_bypass.js --no-pause

# 2. If fails, try OkHttp-specific
frida -U -f com.target.app -l okhttp_ssl_bypass.js --no-pause

# 3. If still fails, try multi-hook
frida -U -f com.target.app -l multi_ssl_bypass.js --no-pause
```

### Step 3: Verify Bypass

```bash
# Setup proxy (Burp/mitmproxy)
adb shell settings put global http_proxy <your-ip>:8080

# Launch app with Frida script
frida -U -f com.target.app -l ssl_bypass.js --no-pause

# Check proxy for HTTPS traffic
# ✅ Success: You see decrypted HTTPS traffic
# ❌ Fail: Connection errors or no traffic
```

## Troubleshooting

### Issue 1: "Class not found"

**Cause:** Class name wrong or not loaded yet

**Solution:**
```bash
# Let app load first, then attach
frida -U -n com.target.app -l ssl_bypass.js
```

### Issue 2: "Method overload not found"

**Cause:** Wrong method signature

**Solution:**
```bash
# Use LLM to generate with proper overload detection
-q 'hook okhttp3.CertificatePinner.check log calls'
# Then check the logs to see the actual signature
```

### Issue 3: Multiple pinning checks

**Cause:** App has redundant checks

**Solution:**
```bash
# Use multi-hook
-q 'hook Check1.verify() return null AND hook Check2.validate() return null AND hook Check3.authenticate() return null'
```

### Issue 4: Native SSL Pinning

**Cause:** Pinning implemented in C/C++ (JNI)

**Current Limitation:** Nossa ferramenta foca em Java/Kotlin

**Workaround:**
- Use objection (tem bypass nativo)
- Ou use frida-trace para encontrar funções nativas

## Advanced Queries

### Conditional Bypass

```bash
# Only bypass for specific domains
-q 'hook okhttp3.CertificatePinner.check - log hostname and return null only if hostname contains api.example.com'
```

### Logging + Bypass

```bash
# See what's being pinned before bypassing
-q 'hook okhttp3.CertificatePinner.check log calls AND return null'
```

### Selective Bypass

```bash
# Bypass pinning but keep other security checks
-q 'hook okhttp3.CertificatePinner.check return null'
# (don't hook TrustManager)
```

## Research Use Cases

### Question 1: How many apps use SSL pinning?

```python
apps = ["app1", "app2", "app3", ...]
pinning_count = 0

for app in apps:
    context = collect_context(app)
    if "CertificatePinner" in context or has_ssl_classes(context):
        pinning_count += 1

print(f"SSL Pinning usage: {pinning_count}/{len(apps)}")
```

### Question 2: Success rate of generic vs specific bypass?

```python
results = {
    "generic": test_bypass(app, "bypass ssl pinning"),
    "specific": test_bypass(app, "hook okhttp3.CertificatePinner.check"),
    "multi": test_bypass(app, "hook X AND hook Y")
}
```

### Question 3: LLM accuracy for SSL bypass?

```python
# Generate script
script = llm.generate("bypass ssl pinning")

# Test
success = execute_and_verify(script)

# Measure
accuracy = successes / total_attempts
```

## Best Practices

1. **Always try generic first** - Faster and often works
2. **Use dry-run for analysis** - Review script before executing
3. **Save successful bypasses** - Build a library
4. **Verify with proxy** - Don't assume bypass worked
5. **Multi-hook for production apps** - They usually have redundancy

## Common Patterns

### Pattern 1: OkHttp Only
```
App uses: OkHttp 3.x/4.x
Bypass: Single hook on CertificatePinner.check
Success Rate: ~80%
```

### Pattern 2: OkHttp + Custom Validator
```
App uses: OkHttp + custom validator class
Bypass: Multi-hook (2 hooks)
Success Rate: ~70%
```

### Pattern 3: Multiple Layers
```
App uses: OkHttp + TrustManager + Network Config
Bypass: Multi-hook (3+ hooks) + possible manual intervention
Success Rate: ~50%
```

## Next Steps

After successful SSL bypass:
1. ✅ Capture and analyze API traffic
2. ✅ Identify sensitive endpoints
3. ✅ Test API security
4. ✅ Document findings for research

## Related Tools

- **Objection**: `objection -g app explore`, then `android sslpinning disable`
- **Frida-Gadget**: For non-rooted devices
- **mitmproxy**: HTTPS proxy for testing
- **Burp Suite**: Commercial alternative