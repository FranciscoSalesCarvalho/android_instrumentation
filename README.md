# FridaForge

**Automated Frida Script Generation for Android Security Analysis using LLMs and Dynamic Application Context**

FridaForge automates the generation of Frida instrumentation scripts from natural language queries. It collects runtime context from the target Android application (loaded classes, methods, libraries, native modules, storage) and uses it to guide an LLM in producing functional, application-specific scripts.

> **Paper:** *FridaForge: Geração Automatizada de Scripts de Instrumentação Dinâmica Assistida por Modelos de Linguagem com Contexto da Aplicação*  
> **Venue:** Salão de Ferramentas — SBSeg 2026

🌐 [Versão em Português](README.pt-br.md)

---

## Table of Contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Usage](#usage)
- [Examples](#examples)
- [Project Structure](#project-structure)
- [Reproducing Experiments](#reproducing-experiments)
- [Demo Video](#demo-video)
- [License](#license)
- [Citation](#citation)

---

## Overview

Dynamic instrumentation with Frida is essential for Android security analysis, but writing effective scripts requires expertise in the Frida API and knowledge of the target application's internals. FridaForge addresses this by:

1. **Collecting runtime context** — classes, methods, libraries, native modules, databases, and SharedPreferences from the running application
2. **Building context-aware prompts** — structuring collected information alongside the analyst's natural language query
3. **Generating executable scripts** — using an LLM to produce Frida JavaScript scripts that reference real application elements
4. **Validating and executing** — automatically extracting, validating, and injecting the script into the target application

Queries can be formulated at three specificity levels:

| Level | Description | Example |
|-------|-------------|---------|
| **Specific** | Full class path and method | `hook com.app.SecurityCheck.isEmulator() return false` |
| **Semi-specific** | Class or method name without full path | `bypass isEmulator from SecurityCheck` |
| **Generic** | High-level intent | `bypass emulator detection` |

---

## Requirements

- **OS:** macOS or Linux
- **Java:** JDK 17+
- **Android:** Device or emulator with root access
- **Frida:** v16+ installed on host (`pip install frida-tools`) and Frida Server on device
- **ADB:** Android Debug Bridge configured and device connected
- **API Key:** Anthropic API key for Claude access

---

## Installation

### 1. Clone the repository

```bash
git clone https://github.com/FranciscoSalesCarvalho/android_instrumentation.git
cd android_instrumentation
```

### 2. Install Frida on host

```bash
pip install frida-tools
frida --version
```

### 3. Install Frida Server on device

```bash
# Download matching version for your device architecture
# Push to device
adb push frida-server /data/local/tmp/
adb shell "chmod 755 /data/local/tmp/frida-server"
adb shell "/data/local/tmp/frida-server &"
```

### 4. Build FridaForge

```bash
# Option A: Use pre-built jar (no compilation needed)
java -jar release/fridaforge.jar --help
 
# Option B: Build from source
./gradlew build
```

### 5. Configure API key

```bash
export ANTHROPIC_API_KEY="your-api-key-here"
```

---

## Quick Start

```bash
# 1. Ensure device is connected and Frida Server is running
adb devices
frida-ps -Ua

# 2. Run FridaForge in interactive mode
./gradlew run --args="-p com.example.app -k $ANTHROPIC_API_KEY -i"

# 3. Type a query
frida-llm> bypass emulator detection
```

---

## Usage

### Interactive Mode (Recommended)

```bash
./gradlew run --args="-p <package_name> -k <api_key> -i"
```

Commands available in interactive mode:

| Command | Description |
|---------|-------------|
| `<any query>` | Generate and execute a Frida script |
| `classes` | List collected application classes |
| `frameworks` | Show detected libraries |
| `stats` | Display context collection statistics |
| `help` | Show available commands |
| `exit` | Quit interactive mode |

### Single Query Mode

```bash
./gradlew run --args="-p <package_name> -k <api_key> -q '<query>'"
```

### Additional Options

| Flag | Description |
|------|-------------|
| `-p` | Target application package name |
| `-k` | Anthropic API key (or use `ANTHROPIC_API_KEY` env var) |
| `-q` | Single query to execute |
| `-i` | Interactive mode |
| `-c` | Context level: `MINIMAL`, `BASIC` (default), `FULL` |
| `-s` | Save generated script to file |
| `-o` | Save collected context to JSON |
| `--dry-run` | Generate script without executing |

---

## Examples

### Bypass Emulator Detection

```bash
# Specific
./gradlew run --args="-p owasp.sat.agoat -q 'hook owasp.sat.agoat.EmulatorDetectionActivity.isEmulator() return false'"

# Generic
./gradlew run --args="-p owasp.sat.agoat -q 'bypass emulator detection'"
```

### Intercept Login Credentials

```bash
./gradlew run --args="-p com.android.insecurebankv2 -q 'intercept credentials from LoginActivity when user performs login'"
```

### Bypass SSL Pinning

```bash
./gradlew run --args="-p owasp.sat.agoat -q 'bypass SSL pinning'"
```

### Intercept Cryptographic Operations

```bash
./gradlew run --args="-p sg.vantagepoint.uncrackable1 -q 'intercept cryptographic operations and convert the result to legible text'"
```

### Multiple Hooks

```bash
# Using AND separator
./gradlew run --args="-p com.example.app -q 'hook Class1.method1() return false AND hook Class2.method2() return false'"
```

---

## Project Structure

```
fridaforge/
├── README.md
├── LICENSE
├── build.gradle.kts
├── settings.gradle.kts
├── examples/                          # Example queries and outputs
│   ├── queries/                       # Sample queries per app
│   └── scripts/                       # Example generated scripts
├── artifacts/                         # Experiment data and results
│   ├── FridaForge_Resultados_N5.xlsx  # Full N=5 experiment results
│   └── context_samples/               # Sample collected contexts
├── demo/                              # Demonstration materials
│   └── video_link.md                  # Link to demo video
└── src/main/kotlin/
    ├── Main.kt                        # CLI entry point
    ├── core/
    │   ├── FridaConnector.kt          # Frida communication
    │   ├── ContextCollector.kt        # Dynamic context collection
    │   ├── QueryRouter.kt             # Query type classification
    │   ├── QueryParser.kt             # Specific query parsing
    │   └── ScriptExecutor.kt          # Script validation & execution
    ├── collectors/
    │   ├── AppInfoCollector.kt        # App metadata
    │   ├── ClassCollector.kt          # Class enumeration
    │   ├── MethodCollector.kt         # Method enumeration
    │   ├── FrameworkDetector.kt       # Library detection
    │   ├── ManifestCollector.kt       # AndroidManifest parsing
    │   ├── StorageCollector.kt        # DB & SharedPrefs detection
    │   └── NativeLibraryCollector.kt  # Native module enumeration
    ├── llm/
    │   ├── LLMClient.kt              # Claude API client
    │   └── PromptBuilder.kt          # Context-aware prompt construction
    └── models/
        ├── AppContext.kt              # Application context model
        ├── ClassInfo.kt               # Class/method data
        └── GeneratedScript.kt         # Script result model
```

---

## Reproducing Experiments

The evaluation described in the paper can be reproduced as follows:

### 1. Benchmark Applications

| ID | Application | Source |
|----|-------------|--------|
| A1 | DIVA | [GitHub](https://github.com/payatu/diva-android) |
| A2 | InsecureBankv2 | [GitHub](https://github.com/dineshshetty/Android-InsecureBankv2) |
| A3 | UnCrackable L1 | [OWASP MASTG](https://mas.owasp.org/crackmes/Android/) |
| A4 | Damn Vulnerable Bank | [GitHub](https://github.com/AsesLabs/DamnVulnerableBank) |
| A5 | AndroGoat | [GitHub](https://github.com/AseemShrey/AndroGoat) |

### 2. Environment Setup

```bash
# Android emulator with API 36, root enabled
adb root

# Frida Server v17.4.1
adb push frida-server-17.4.1-android-arm64 /data/local/tmp/frida-server
adb shell "chmod 755 /data/local/tmp/frida-server"
adb shell "/data/local/tmp/frida-server &"
```

### 3. Running Tests

```bash
# Install target app
adb install <app.apk>

# Run FridaForge
./gradlew run --args="-p <package_name> -k $ANTHROPIC_API_KEY -i"

# Reset between executions
adb shell am force-stop <package_name>
adb shell pm clear <package_name>
```

### 4. Experiment Results

Full results (N=5, 175 executions) are available in `artifacts/FridaForge_Resultados_N5.xlsx`.

---

## Demo Video

A demonstration video showing the installation, configuration, and usage of FridaForge is available at:

**[Demo Video](https://drive.google.com/drive/folders/1adbSMc6c9pAS0T3duMDPOnGjah7tJcJK?usp=drive_link)**

The video covers two scenarios:
1. Bypass of emulator detection in AndroGoat
2. Interception of cryptographic operations in UnCrackable L1

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---