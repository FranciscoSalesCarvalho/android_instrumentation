#!/bin/bash
# FridaForge - Setup Script
# Verifies dependencies and prepares the environment

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "========================================="
echo "  FridaForge - Environment Setup"
echo "========================================="
echo ""

ERRORS=0

# Check Java
echo -n "[1/6] Java JDK 17+... "
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
    if [ "$JAVA_VERSION" -ge 17 ] 2>/dev/null; then
        echo -e "${GREEN}OK${NC} (version $JAVA_VERSION)"
    else
        echo -e "${RED}FAILED${NC} (version $JAVA_VERSION, need 17+)"
        ERRORS=$((ERRORS+1))
    fi
else
    echo -e "${RED}NOT FOUND${NC}"
    echo "    Install: https://adoptium.net/"
    ERRORS=$((ERRORS+1))
fi

# Check ADB
echo -n "[2/6] ADB... "
if command -v adb &> /dev/null; then
    echo -e "${GREEN}OK${NC} ($(adb version | head -1))"
else
    echo -e "${RED}NOT FOUND${NC}"
    echo "    Install: https://developer.android.com/tools/releases/platform-tools"
    ERRORS=$((ERRORS+1))
fi

# Check Frida
echo -n "[3/6] Frida tools... "
if command -v frida &> /dev/null; then
    echo -e "${GREEN}OK${NC} ($(frida --version))"
else
    echo -e "${RED}NOT FOUND${NC}"
    echo "    Install: pip install frida-tools"
    ERRORS=$((ERRORS+1))
fi

# Check device connection
echo -n "[4/6] Android device... "
if adb devices 2>/dev/null | grep -q "device$"; then
    DEVICE=$(adb devices | grep "device$" | head -1 | cut -f1)
    echo -e "${GREEN}OK${NC} ($DEVICE)"
else
    echo -e "${YELLOW}NOT CONNECTED${NC}"
    echo "    Connect a device or start an emulator"
    ERRORS=$((ERRORS+1))
fi

# Check Frida Server on device
echo -n "[5/6] Frida Server on device... "
if adb shell "ps | grep frida-server" &> /dev/null 2>&1; then
    echo -e "${GREEN}RUNNING${NC}"
elif adb shell "ls /data/local/tmp/frida-server" &> /dev/null 2>&1; then
    echo -e "${YELLOW}INSTALLED BUT NOT RUNNING${NC}"
    echo "    Start with: adb shell '/data/local/tmp/frida-server &'"
    ERRORS=$((ERRORS+1))
else
    echo -e "${RED}NOT FOUND${NC}"
    echo "    Download from: https://github.com/frida/frida/releases"
    echo "    Push with: adb push frida-server /data/local/tmp/"
    ERRORS=$((ERRORS+1))
fi

# Check API key
echo -n "[6/6] Anthropic API key... "
if [ -n "$ANTHROPIC_API_KEY" ]; then
    echo -e "${GREEN}SET${NC}"
else
    echo -e "${YELLOW}NOT SET${NC}"
    echo "    Set with: export ANTHROPIC_API_KEY='your-key-here'"
    ERRORS=$((ERRORS+1))
fi

echo ""
echo "========================================="
if [ $ERRORS -eq 0 ]; then
    echo -e "${GREEN}All checks passed!${NC}"
    echo ""
    echo "Run FridaForge:"
    echo "  java -jar release/fridaforge.jar -p <package> -k \$ANTHROPIC_API_KEY -i"
    echo ""
    echo "Or build from source:"
    echo "  ./gradlew build"
    echo "  ./gradlew run --args=\"-p <package> -k \$ANTHROPIC_API_KEY -i\""
else
    echo -e "${RED}$ERRORS issue(s) found.${NC} Fix them before running FridaForge."
fi
echo "========================================="