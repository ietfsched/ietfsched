#!/bin/bash
# Generate code coverage report from regression tests
# This helps identify unused methods/classes that aren't exercised by tests

set -e

cd "$(dirname "$0")"

echo "=== Generating Code Coverage Report ==="
echo ""
echo "This will:"
echo "  1. Run all regression tests with coverage enabled"
echo "  2. Generate HTML coverage report"
echo "  3. Open the report in your browser"
echo ""

# Check if device/emulator is available
# Try to find adb in common locations
ADB_CMD="adb"
if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
    ADB_CMD="$HOME/Library/Android/sdk/platform-tools/adb"
elif [ -f "$ANDROID_HOME/platform-tools/adb" ]; then
    ADB_CMD="$ANDROID_HOME/platform-tools/adb"
fi

DEVICES=$($ADB_CMD devices 2>/dev/null | grep -v "List" | grep "device$" | awk '{print $1}')
if [ -z "$DEVICES" ]; then
    echo "❌ Error: No devices/emulators connected"
    echo "Please connect a device or start an emulator"
    exit 1
fi

echo "Found devices: $DEVICES"
echo ""

# Run tests with coverage (coverage is automatically collected when testCoverageEnabled=true)
echo "Running tests with coverage enabled..."
./gradlew connectedDebugAndroidTest

# Coverage reports are generated automatically, but we need to create the HTML report
echo ""
echo "Generating HTML coverage report..."
./gradlew createDebugCoverageReport

# Find the coverage report (location varies by Android Gradle Plugin version)
COVERAGE_HTML="app/build/reports/coverage/androidTest/debug/connected/index.html"
if [ ! -f "$COVERAGE_HTML" ]; then
    # Try alternative locations
    COVERAGE_HTML="app/build/reports/coverage/debug/index.html"
fi
if [ ! -f "$COVERAGE_HTML" ]; then
    # Look for any HTML file in coverage directories
    COVERAGE_HTML=$(find app/build/reports/coverage -name "index.html" -type f 2>/dev/null | head -1)
fi

if [ -f "$COVERAGE_HTML" ]; then
    echo ""
    echo "✓ Coverage report generated: $COVERAGE_HTML"
    echo ""
    echo "To find unused code:"
    echo "  1. Open the HTML report"
    echo "  2. Look for files/classes with 0% coverage"
    echo "  3. Review methods with 0% coverage (may be unused)"
    echo ""
    
    # Try to open in browser (macOS)
    if command -v open >/dev/null 2>&1; then
        echo "Opening report in browser..."
        open "$COVERAGE_HTML"
    elif command -v xdg-open >/dev/null 2>&1; then
        echo "Opening report in browser..."
        xdg-open "$COVERAGE_HTML"
    else
        echo "Please open manually: file://$(pwd)/$COVERAGE_HTML"
    fi
else
    echo "❌ Error: Coverage report not found at $COVERAGE_HTML"
    echo "Check build output for errors"
    exit 1
fi

