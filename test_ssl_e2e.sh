#!/bin/bash

# SSL Pinning Bypass - Complete End-to-End Test
# This script performs ALL steps: CA setup, proxy config, bypass, test, cleanup
# Usage: ./test_ssl_e2e.sh <package_name> <api_key>

set -e

PACKAGE=$1
API_KEY=$2

if [ -z "$PACKAGE" ] || [ -z "$API_KEY" ]; then
    echo "Usage: ./test_ssl_e2e.sh <package_name> <api_key>"
    echo ""
    echo "Example: ./test_ssl_e2e.sh com.example.app sk-ant-..."
    exit 1
fi

echo "╔══════════════════════════════════════════════════════════╗"
echo "║   SSL Pinning Bypass - Complete E2E Test                ║"
echo "║   From Zero to Working SSL Bypass                       ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

# Prerequisites check
echo "🔍 Checking prerequisites..."
echo ""

# Check adb
if ! command -v adb &> /dev/null; then
    echo "❌ ADB not found"
    echo "   Install: apt install android-tools-adb  (Linux)"
    echo "   Install: brew install android-platform-tools  (Mac)"
    exit 1
fi
echo "✅ ADB found"

# Check device
if ! adb devices | grep -q "device$"; then
    echo "❌ No Android device/emulator connected"
    echo ""
    echo "   Connect device:"
    echo "   1. Enable USB debugging on device"
    echo "   2. Connect via USB"
    echo "   3. Run: adb devices"
    echo ""
    echo "   Or start emulator:"
    echo "   emulator -avd YOUR_AVD_NAME"
    exit 1
fi
echo "✅ Device connected"

# Check app installed
if ! adb shell pm list packages | grep -q "$PACKAGE"; then
    echo "❌ App $PACKAGE not installed on device"
    echo "   Install the APK first: adb install app.apk"
    exit 1
fi
echo "✅ App installed"

# Check Burp
echo ""
echo "📡 Checking for Burp Suite..."
if ! nc -zv 127.0.0.1 8080 2>&1 | grep -q "succeeded\|open"; then
    echo "⚠️  Burp Suite not detected on port 8080"
    echo ""
    echo "   Start Burp Suite:"
    echo "   1. Open Burp Suite"
    echo "   2. Go to: Proxy → Options"
    echo "   3. Proxy Listeners → Check 127.0.0.1:8080 is running"
    echo "   4. If not, click 'Add' and create listener on 127.0.0.1:8080"
    echo ""
    read -p "   Press Enter when Burp is running (or Ctrl+C to abort)..."

    # Check again
    if ! nc -zv 127.0.0.1 8080 2>&1 | grep -q "succeeded\|open"; then
        echo "❌ Still cannot connect to Burp on port 8080"
        exit 1
    fi
fi
echo "✅ Burp Suite is running on port 8080"

echo ""
echo "✅ All prerequisites met!"
echo ""

# Phase 1: CA Certificate Setup
echo "══════════════════════════════════════════════════════════"
echo "PHASE 1: CA Certificate Setup (Automated)"
echo "══════════════════════════════════════════════════════════"
echo ""
echo "This will:"
echo "  1. Download Burp's CA certificate"
echo "  2. Convert to Android format"
echo "  3. Push to device"
echo "  4. Guide you through manual installation"
echo ""
read -p "Press Enter to start..."
echo ""

./gradlew run --quiet --args="--setup-ca"

echo ""
read -p "Is the CA certificate installed on device? (y/N): "
CA_INSTALLED=$REPLY

if [ "$CA_INSTALLED" != "y" ] && [ "$CA_INSTALLED" != "Y" ]; then
    echo ""
    echo "⚠️  CA certificate not installed"
    echo "   SSL bypass will not work without it"
    echo ""
    read -p "Continue anyway for testing? (y/N): "
    if [ "$REPLY" != "y" ] && [ "$REPLY" != "Y" ]; then
        echo "Aborted. Please install CA certificate and try again."
        exit 1
    fi
fi

# Phase 2: Run SSL Bypass
echo ""
echo "══════════════════════════════════════════════════════════"
echo "PHASE 2: SSL Pinning Bypass (Automated)"
echo "══════════════════════════════════════════════════════════"
echo ""
echo "This will:"
echo "  1. Configure proxy on device"
echo "  2. Setup port forwarding"
echo "  3. Generate Frida bypass script using AI"
echo "  4. Execute the bypass"
echo ""
read -p "Press Enter to start SSL bypass..."
echo ""

# Run with output saved
./gradlew run --args="-p $PACKAGE \
    -k $API_KEY \
    -q 'bypass ssl pinning' \
    -s ssl_bypass_$PACKAGE.js"

BYPASS_SUCCESS=$?

# Phase 3: Manual Testing
echo ""
echo "══════════════════════════════════════════════════════════"
echo "PHASE 3: Manual Testing & Verification"
echo "══════════════════════════════════════════════════════════"
echo ""

if [ $BYPASS_SUCCESS -eq 0 ]; then
    echo "✅ SSL bypass script executed successfully"
    echo ""
    echo "📱 Now do the following:"
    echo ""
    echo "1. Open Burp Suite → Proxy → HTTP history"
    echo ""
    echo "2. Open the app on your device"
    echo ""
    echo "3. Perform actions that make HTTPS requests:"
    echo "   • Login"
    echo "   • Load main screen"
    echo "   • Make API calls"
    echo ""
    echo "4. Check Burp for HTTPS traffic"
    echo ""
    echo "✅ SUCCESS indicators:"
    echo "   • You see HTTPS requests in Burp"
    echo "   • Requests are decrypted (readable JSON/XML)"
    echo "   • App works normally"
    echo ""
    echo "❌ FAILURE indicators:"
    echo "   • No traffic in Burp"
    echo "   • App shows SSL/network errors"
    echo "   • App crashes"
    echo ""
else
    echo "❌ SSL bypass script execution failed"
    echo "   Check errors above"
fi

echo ""
read -p "Press Enter when you've finished testing..."

# Phase 4: Results & Cleanup
echo ""
echo "══════════════════════════════════════════════════════════"
echo "PHASE 4: Results & Cleanup"
echo "══════════════════════════════════════════════════════════"
echo ""

read -p "Did the SSL bypass work? (y/N): "
if [ "$REPLY" = "y" ] || [ "$REPLY" = "Y" ]; then
    echo ""
    echo "🎉 SUCCESS! SSL Pinning bypassed!"
    echo ""
    echo "📝 What you accomplished:"
    echo "   ✓ Installed Burp CA certificate"
    echo "   ✓ Configured device proxy"
    echo "   ✓ Generated AI-powered bypass script"
    echo "   ✓ Successfully intercepted HTTPS traffic"
    echo ""
    echo "💾 Generated files:"
    echo "   • ssl_bypass_$PACKAGE.js - The bypass script"
    echo ""
    echo "🔄 To reuse:"
    echo "   frida -U -f $PACKAGE -l ssl_bypass_$PACKAGE.js --no-pause"
else
    echo ""
    echo "⚠️  SSL bypass didn't work as expected"
    echo ""
    echo "🔍 Troubleshooting:"
    echo "   1. Check Frida logs for errors"
    echo "   2. Verify CA certificate is installed correctly"
    echo "   3. Check if app uses native SSL pinning (harder to bypass)"
    echo "   4. Try manual bypass: frida -U -f $PACKAGE -l ssl_bypass_$PACKAGE.js"
    echo ""
    echo "📚 For help, check:"
    echo "   • SSL_PINNING_GUIDE.md"
    echo "   • Frida documentation"
fi

echo ""
read -p "Remove proxy configuration? (Y/n): "
if [ "$REPLY" != "n" ] && [ "$REPLY" != "N" ]; then
    ./gradlew run --quiet --args="--cleanup"
    echo "✅ Proxy configuration removed"
else
    echo "ℹ️  Proxy still configured"
    echo "   Remove later with: ./gradlew run --args='--cleanup'"
fi

echo ""
echo "══════════════════════════════════════════════════════════"
echo "✅ E2E Test Complete!"
echo ""
echo "Summary:"
echo "  Package: $PACKAGE"
echo "  CA Installed: $CA_INSTALLED"
echo "  Bypass Executed: $([ $BYPASS_SUCCESS -eq 0 ] && echo 'Yes' || echo 'No')"
echo "  Script Saved: ssl_bypass_$PACKAGE.js"
echo ""
echo "Thank you for testing! 🚀"
echo "══════════════════════════════════════════════════════════"