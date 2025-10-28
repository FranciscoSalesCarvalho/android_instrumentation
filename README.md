# Frida-LLM Tool 🤖

AI-Powered Frida instrumentation tool for Android security research.

## Features

✅ **Smart Query Routing** - Automatically detects query type and optimizes processing  
✅ **Context-Aware** - Collects app structure intelligently  
✅ **Claude AI Integration** - Generates Frida scripts using Claude API  
✅ **On-Demand Collection** - Only collects what's needed  
✅ **Interactive Mode** - REPL for rapid testing

## Installation

### Prerequisites

```bash
# Frida tools
pip install frida-tools

# Verify installation
frida --version
```

### Build

```bash
./gradlew build
```

## Usage

### 1. Setup API Key

```bash
# Option A: Environment variable
export ANTHROPIC_API_KEY="your-api-key-here"

# Option B: Command line flag
# Use -k flag (see examples below)
```

### 2. Query Types

#### 🎯 Specific Query (Fastest - 1-2s)

When you know the exact class and method:

```bash
./gradlew run --args="-p com.example.app \
  -k YOUR_API_KEY \
  -q 'hook com.example.SecurityCheck.isEmulator() return false'"
```

**Output:**
- Parses query instantly
- Generates targeted script
- Executes immediately

#### ⚡ Semi-Specific Query (Fast - 3-5s)

When you know some keywords:

```bash
./gradlew run --args="-p com.example.app \
  -k YOUR_API_KEY \
  -q 'bypass isEmulator method from SecurityCheck'"
```

**Output:**
- Searches for matching classes
- Collects methods on-demand
- Generates contextual script

#### 🔎 Generic Query (Thorough - 5-10s)

When you only know what you want:

```bash
./gradlew run --args="-p com.example.app \
  -k YOUR_API_KEY \
  -q 'bypass emulator detection'"
```

**Output:**
- Full context collection
- Intelligent class prioritization
- Comprehensive script generation

### 3. Additional Options

#### Save Generated Script

```bash
./gradlew run --args="-p com.example.app \
  -k YOUR_API_KEY \
  -q 'hook com.example.Class.method() return false' \
  -s output.js"
```

#### Dry Run (Generate but don't execute)

```bash
./gradlew run --args="-p com.example.app \
  -k YOUR_API_KEY \
  -q 'bypass root check' \
  --dry-run"
```

#### Save Context for Analysis

```bash
./gradlew run --args="-p com.example.app \
  -c FULL \
  -o context.json"
```

### 4. Interactive Mode

```bash
./gradlew run --args="-p com.example.app -k YOUR_API_KEY -i"
```

**Commands:**
```
frida-llm> hook com.example.SecurityCheck.isEmulator() return false
frida-llm> bypass root detection
frida-llm> stats
frida-llm> classes
frida-llm> frameworks
frida-llm> help
frida-llm> exit
```

## Examples

### Single Hook - Emulator Detection Bypass

```bash
# Specific
./gradlew run --args="-p com.pentestmobile.appemulator \
  -q 'hook com.pentestmobile.appemulator.SecurityCheck.isEmulator() return false'"

# Generic
./gradlew run --args="-p com.pentestmobile.appemulator \
  -q 'bypass emulator detection'"
```

### Multiple Hooks - Comprehensive Emulator Bypass 🆕

When an app has multiple emulator detection checks:

```bash
./gradlew run --args="-p com.example.app \
  -q 'hook com.example.SecurityCheck.isEmulator() return false AND hook com.example.DeviceValidator.detectEmulator() return false'"
```

**Separators supported:**
- `AND` / `OR` (case insensitive)
- Comma `,`
- Semicolon `;`
- New line

**Examples:**

```bash
# Using AND
-q 'hook Class1.method1() return false AND hook Class2.method2() return false'

# Using comma
-q 'hook Class1.method1() return false, hook Class2.method2() return false'

# Using semicolon
-q 'bypass Class1.check1(); bypass Class2.check2()'

# Multi-line (in script)
-q 'hook Class1.method1() return false
hook Class2.method2() return false
hook Class3.method3() return false'
```

### Multiple Hooks - Root Detection Bypass

```bash
./gradlew run --args="-p com.example.bankapp \
  -q 'hook com.example.security.RootChecker.isRooted() return false, hook com.example.security.RootDetector.checkRoot() return false, hook com.example.utils.SecurityUtils.hasRootAccess() return false'"
```

### SSL Pinning Bypass

```bash
./gradlew run --args="-p com.example.app \
  -q 'bypass ssl pinning'"
```

### Method Logging

```bash
./gradlew run --args="-p com.example.app \
  -q 'intercept com.example.api.LoginService.login log calls'"
```

## Context Levels

### MINIMAL
- Only class names
- Fastest collection (~2s)
- Use when you know what you're looking for

```bash
-c MINIMAL
```

### BASIC (Default)
- App classes + frameworks
- Balanced speed/info (~5s)
- Best for most cases

```bash
-c BASIC
```

### FULL
- All classes + methods + manifest
- Slowest but most complete (~15s)
- Use for thorough analysis

```bash
-c FULL
```

## Research Usage

### Collect Metrics

```bash
# Generate script without execution
./gradlew run --args="-p TARGET_APP \
  -q 'QUERY' \
  --dry-run \
  -s generated_script.js"

# Manually validate and execute
frida -U -f TARGET_APP -l generated_script.js --no-pause
```

### Compare Query Types

```bash
# Test specific query
time ./gradlew run --args="-p APP -q 'hook com.example.Class.method() return false'"

# Test generic query  
time ./gradlew run --args="-p APP -q 'bypass check'"
```

### Save Full Context

```bash
./gradlew run --args="-p TARGET_APP -c FULL -o analysis/context.json"
```

## Troubleshooting

### "Failed to connect"

```bash
# Check if app is installed
adb shell pm list packages | grep YOUR_PACKAGE

# Check if Frida server is running
adb shell "ps | grep frida"

# Check device connection
frida-ps -Ua
```

### "No API key"

```bash
# Set environment variable
export ANTHROPIC_API_KEY="sk-ant-..."

# Or use -k flag
-k "sk-ant-..."
```

### "Script validation failed"

The LLM-generated script has syntax errors. Use `--dry-run` to see the script and debug manually.

### "Class not found"

The app may not have loaded the class yet. Try:
1. Interact with the app feature first
2. Use spawn mode (app will be launched automatically)
3. Check class name is correct with JADX

## Project Structure

```
src/main/kotlin/
├── Main.kt                 # CLI entry point
├── core/
│   ├── FridaConnector.kt   # Frida communication
│   ├── ContextCollector.kt # Smart context collection
│   ├── QueryRouter.kt      # Query type detection
│   ├── QueryParser.kt      # Specific query parsing
│   └── ScriptExecutor.kt   # Script execution
├── collectors/
│   ├── AppInfoCollector.kt
│   ├── ClassCollector.kt
│   ├── FrameworkDetector.kt
│   └── ManifestCollector.kt
├── llm/
│   ├── LLMClient.kt        # Claude API client
│   └── PromptBuilder.kt    # Prompt engineering
└── models/
    ├── AppContext.kt       # Data models
    └── ...
```

## License

MIT License - See LICENSE file

## Contributing

This is a research project. Contributions welcome!

## Citation

If you use this tool in your research, please cite:

```bibtex
@misc{frida-llm-tool,
  title={Frida-LLM: AI-Powered Mobile Instrumentation},
  author={Your Name},
  year={2025},
  url={https://github.com/yourusername/frida-llm-tool}
}
```