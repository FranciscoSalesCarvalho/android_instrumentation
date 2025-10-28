# Multi-Hook Examples 🔗

## Overview

The tool now supports hooking multiple methods in a single query, which is essential for bypassing comprehensive security checks.

## Syntax

### Separators

You can use any of these separators between hooks:
- `AND` (case insensitive)
- `OR` (case insensitive)
- `,` (comma)
- `;` (semicolon)
- New line

All separators work the same way - they just split your query into multiple hooks.

## Real-World Examples

### 1. Emulator Detection (Multiple Checks)

Many apps check for emulators in multiple places:

```bash
./gradlew run --args="-p com.example.app \
  -k YOUR_API_KEY \
  -q 'hook com.example.SecurityCheck.isEmulator() return false AND hook com.example.DeviceValidator.detectEmulator() return false AND hook com.example.utils.SystemInfo.checkEmulator() return false'"
```

**Alternative syntax (comma):**
```bash
-q 'hook com.example.SecurityCheck.isEmulator() return false, hook com.example.DeviceValidator.detectEmulator() return false, hook com.example.utils.SystemInfo.checkEmulator() return false'
```

### 2. Root Detection (Comprehensive Bypass)

Apps often have multiple root detection mechanisms:

```bash
./gradlew run --args="-p com.banking.app \
  -q 'hook com.banking.security.RootChecker.isRooted() return false AND hook com.banking.security.RootDetector.checkRoot() return false AND hook com.banking.security.RootDetector.checkSuBinary() return false AND hook com.banking.utils.SecurityUtils.hasRootAccess() return false'"
```

### 3. Multiple Security Checks

Bypass different types of checks at once:

```bash
./gradlew run --args="-p com.secure.app \
  -q 'hook com.secure.SecurityCheck.isEmulator() return false AND hook com.secure.SecurityCheck.isRooted() return false AND hook com.secure.SecurityCheck.isDebuggable() return false'"
```

### 4. Using Semicolon Separator

```bash
./gradlew run --args="-p com.example.app \
  -q 'bypass com.example.Check1.verify(); bypass com.example.Check2.validate(); bypass com.example.Check3.authenticate()'"
```

### 5. Multi-line Format (for scripts)

Create a file `hooks.txt`:
```
hook com.example.SecurityCheck.isEmulator() return false
hook com.example.DeviceValidator.detectEmulator() return false  
hook com.example.RootChecker.isRooted() return false
hook com.example.DebugDetector.isDebuggable() return false
```

Then use:
```bash
./gradlew run --args="-p com.example.app -q \"$(cat hooks.txt)\""
```

## Output Example

When you use multiple hooks:

```
🔍 Analyzing query...
Query type: SPECIFIC

✅ Specific query detected - Fast path!

📋 Parsed Multi-Hook Query (3 hooks):

   Hook 1:
     Class: com.example.SecurityCheck
     Method: isEmulator
     Action: RETURN_FALSE
     Return: false

   Hook 2:
     Class: com.example.DeviceValidator
     Method: detectEmulator
     Action: RETURN_FALSE
     Return: false

   Hook 3:
     Class: com.example.RootChecker
     Method: isRooted
     Action: RETURN_FALSE
     Return: false

🤖 Generating Frida script with Claude...

✅ Script generated (2847 tokens used)

📜 Generated Script:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Java.perform(function() {
    console.log("[+] Starting multi-hook script");
    
    // Hook 1: SecurityCheck.isEmulator
    try {
        var SecurityCheck = Java.use("com.example.SecurityCheck");
        SecurityCheck.isEmulator.implementation = function() {
            console.log("[Hook 1] SecurityCheck.isEmulator() bypassed");
            return false;
        };
        console.log("[+] Hook 1 installed successfully");
    } catch(e) {
        console.error("[Hook 1] Failed: " + e);
    }
    
    // Hook 2: DeviceValidator.detectEmulator
    try {
        var DeviceValidator = Java.use("com.example.DeviceValidator");
        DeviceValidator.detectEmulator.implementation = function() {
            console.log("[Hook 2] DeviceValidator.detectEmulator() bypassed");
            return false;
        };
        console.log("[+] Hook 2 installed successfully");
    } catch(e) {
        console.error("[Hook 2] Failed: " + e);
    }
    
    // Hook 3: RootChecker.isRooted
    try {
        var RootChecker = Java.use("com.example.RootChecker");
        RootChecker.isRooted.implementation = function() {
            console.log("[Hook 3] RootChecker.isRooted() bypassed");
            return false;
        };
        console.log("[+] Hook 3 installed successfully");
    } catch(e) {
        console.error("[Hook 3] Failed: " + e);
    }
    
    console.log("[+] All hooks installed");
});
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

## Interactive Mode

In interactive mode, you can use the same syntax:

```
frida-llm> hook com.example.Check1.verify() return false AND hook com.example.Check2.validate() return false

frida-llm> bypass com.example.Security.isEmulator(), bypass com.example.Security.isRooted()
```

## Tips

### 1. Keep It Organized

For many hooks, use multi-line format for readability:

```bash
-q "hook com.example.Check1.method1() return false
hook com.example.Check2.method2() return false
hook com.example.Check3.method3() return false"
```

### 2. Mix Actions

You can use different actions for different hooks:

```bash
-q "hook com.example.Security.isEmulator() return false AND hook com.example.api.LoginService.login log calls"
```

### 3. Save Complex Queries

For complex multi-hook scenarios, save to file and reuse:

```bash
echo "hook Class1.m1() return false AND hook Class2.m2() return false" > my_hooks.txt
./gradlew run --args="-p APP -q \"$(cat my_hooks.txt)\""
```

### 4. Dry Run First

Test with `--dry-run` to see the generated script before executing:

```bash
./gradlew run --args="-p APP -q 'MULTI_HOOK_QUERY' --dry-run -s output.js"
```

## Common Patterns

### Pattern 1: Same Action, Multiple Classes
```
hook Class1.check() return false AND hook Class2.check() return false AND hook Class3.check() return false
```

### Pattern 2: Different Methods, Same Class
```
hook com.example.Security.isEmulator() return false AND hook com.example.Security.isRooted() return false
```

### Pattern 3: Mixed Security Checks
```
hook com.example.EmulatorCheck.detect() return false, hook com.example.RootCheck.verify() return false, hook com.example.DebugCheck.isAttached() return false
```

## Limitations

- Maximum ~10 hooks per query (for optimal LLM performance)
- All hooks must be valid Frida hook syntax
- Each hook is tried independently (one failure won't break others)

## Research Use Cases

Perfect for studying:
- How many checks does the app have?
- Are all checks in the same class?
- What's the success rate of multi-hook vs single-hook scripts?
- Does hook order matter?

## Next Steps

After generating multi-hook scripts:
1. Analyze which hooks are actually triggered
2. Identify redundant checks
3. Optimize the bypass strategy
4. Document patterns for your research