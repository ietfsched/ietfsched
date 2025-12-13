#!/bin/bash

# Script to run Espresso tests on connected devices/emulators
# Automatically connects to emulator if running, and uses physical devices if available

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== IETF Schedule App - Espresso Test Runner ==="
echo ""

# Function to find adb
find_adb() {
    # Try common locations for adb
    if command -v adb &> /dev/null; then
        ADB_CMD="adb"
        return 0
    fi
    
    # Check common Android SDK locations
    if [ -n "$ANDROID_HOME" ]; then
        ADB_PATH="$ANDROID_HOME/platform-tools/adb"
        if [ -f "$ADB_PATH" ]; then
            ADB_CMD="$ADB_PATH"
            return 0
        fi
    fi
    
    # Check ~/Library/Android/sdk (macOS default)
    if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB_CMD="$HOME/Library/Android/sdk/platform-tools/adb"
        return 0
    fi
    
    # Check ~/Android/Sdk (Linux default)
    if [ -f "$HOME/Android/Sdk/platform-tools/adb" ]; then
        ADB_CMD="$HOME/Android/Sdk/platform-tools/adb"
        return 0
    fi
    
    echo "Error: adb not found"
    echo "Please ensure Android SDK platform-tools is installed and either:"
    echo "  1. Add it to your PATH, or"
    echo "  2. Set ANDROID_HOME environment variable"
    exit 1
}

# Function to check if adb is available
check_adb() {
    find_adb
    export PATH="$(dirname "$ADB_CMD"):$PATH"
    echo "Using adb: $ADB_CMD"
}

# Function to find emulator command
find_emulator() {
    if command -v emulator &> /dev/null; then
        EMULATOR_CMD="emulator"
        return 0
    fi
    
    # Check common Android SDK locations
    if [ -n "$ANDROID_HOME" ]; then
        EMULATOR_PATH="$ANDROID_HOME/emulator/emulator"
        if [ -f "$EMULATOR_PATH" ]; then
            EMULATOR_CMD="$EMULATOR_PATH"
            return 0
        fi
    fi
    
    # Check ~/Library/Android/sdk (macOS default)
    if [ -f "$HOME/Library/Android/sdk/emulator/emulator" ]; then
        EMULATOR_CMD="$HOME/Library/Android/sdk/emulator/emulator"
        return 0
    fi
    
    # Check ~/Android/Sdk (Linux default)
    if [ -f "$HOME/Android/Sdk/emulator/emulator" ]; then
        EMULATOR_CMD="$HOME/Android/Sdk/emulator/emulator"
        return 0
    fi
    
    return 1
}

# Function to start emulator if not running
start_emulator() {
    echo "Checking for running emulators..."
    
    # Check if any emulator is already running
    if "$ADB_CMD" devices | grep -q "emulator.*device$"; then
        echo "✓ Emulator already running"
        return 0
    fi
    
    # Try to start default emulator (optional - don't fail if it doesn't work)
    echo "No emulator running, attempting to start one..."
    
    # Find emulator command
    if find_emulator; then
        # List available AVDs
        AVD_LIST=$("$EMULATOR_CMD" -list-avds 2>/dev/null | head -1)
        if [ -n "$AVD_LIST" ]; then
            echo "Starting emulator: $AVD_LIST"
            "$EMULATOR_CMD" -avd "$AVD_LIST" > /dev/null 2>&1 &
            EMULATOR_PID=$!
            
            # Wait for emulator to boot (with timeout using gtimeout)
            echo "Waiting for emulator to boot (max 60 seconds)..."
            if gtimeout 60 "$ADB_CMD" wait-for-device 2>/dev/null; then
                echo "✓ Emulator device detected"
            else
                echo "⚠ Emulator startup timed out, continuing with available devices"
                kill $EMULATOR_PID 2>/dev/null || true
                unset EMULATOR_PID
                return 1
            fi
            
            # Wait for emulator to be ready (with timeout using gtimeout)
            echo "Waiting for emulator to be ready..."
            if gtimeout 60 bash -c "while ! \"$ADB_CMD\" shell 'getprop sys.boot_completed' 2>/dev/null | grep -q '1'; do sleep 1; done"; then
                echo "✓ Emulator started and ready"
                return 0
            else
                echo "⚠ Emulator boot timed out, continuing with available devices"
                return 1
            fi
        else
            echo "⚠ No AVD found"
        fi
    else
        echo "⚠ Emulator command not found"
    fi
    
    return 1
}

# Function to get API level for a device
get_api_level() {
    local device_id=$1
    "$ADB_CMD" -s "$device_id" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r\n'
}

# Function to filter compatible devices (API 35 or lower)
filter_compatible_devices() {
    COMPATIBLE_DEVICES=()
    API_35_DEVICES=()
    
    while read line; do
        DEVICE_ID=$(echo "$line" | awk '{print $1}')
        API_LEVEL=$(get_api_level "$DEVICE_ID")
        
        if [ -n "$API_LEVEL" ]; then
            if [ "$API_LEVEL" -le 35 ]; then
                COMPATIBLE_DEVICES+=("$DEVICE_ID")
                if [ "$API_LEVEL" -eq 35 ]; then
                    API_35_DEVICES+=("$DEVICE_ID")
                fi
            fi
        fi
    done < <("$ADB_CMD" devices -l | grep -E "device$|emulator")
}

# Function to list connected devices and check API levels
list_devices() {
    echo ""
    echo "Connected devices:"
    HAS_API_36=false
    HAS_COMPATIBLE=false
    
    # Collect device info first
    while read line; do
        DEVICE_ID=$(echo "$line" | awk '{print $1}')
        DEVICE_TYPE=""
        if echo "$line" | grep -q "emulator"; then
            DEVICE_TYPE="(emulator)"
        else
            DEVICE_TYPE="(physical device)"
        fi
        
        API_LEVEL=$(get_api_level "$DEVICE_ID")
        if [ -n "$API_LEVEL" ]; then
            if [ "$API_LEVEL" -eq 36 ]; then
                echo "  - $DEVICE_ID $DEVICE_TYPE [API $API_LEVEL] ⚠️  SKIPPED: API 36 not supported by Espresso"
                HAS_API_36=true
            elif [ "$API_LEVEL" -le 35 ]; then
                if [ "$API_LEVEL" -eq 35 ]; then
                    echo "  - $DEVICE_ID $DEVICE_TYPE [API $API_LEVEL] ✓ (preferred)"
                else
                    echo "  - $DEVICE_ID $DEVICE_TYPE [API $API_LEVEL] ✓"
                fi
                HAS_COMPATIBLE=true
            fi
        else
            echo "  - $DEVICE_ID $DEVICE_TYPE [API unknown]"
        fi
    done < <("$ADB_CMD" devices -l | grep -E "device$|emulator")
    
    if [ "$HAS_API_36" = true ] && [ "$HAS_COMPATIBLE" = false ]; then
        echo ""
        echo "❌ ERROR: No compatible devices found (API 35 or lower required)"
        echo "   Espresso tests do not support Android API 36 (Android 16)"
        echo "   Please use an API 35 or lower emulator/device for testing."
        exit 1
    elif [ "$HAS_API_36" = true ]; then
        echo ""
        echo "ℹ️  Note: API 36 devices will be skipped (not supported by Espresso)"
    fi
}

# Main execution
check_adb

# Start emulator if needed (don't fail if it doesn't work)
start_emulator || true

# List all connected devices
list_devices

# Filter compatible devices
filter_compatible_devices

# Check if any compatible device is available
if [ ${#COMPATIBLE_DEVICES[@]} -eq 0 ]; then
    echo ""
    echo "❌ Error: No compatible devices available (API 35 or lower required)"
    echo "Please:"
    echo "  1. Start an API 35 or lower emulator, or"
    echo "  2. Connect a physical device via USB with USB debugging enabled (API 35 or lower)"
    exit 1
fi

# Prefer API 35 devices if available
if [ ${#API_35_DEVICES[@]} -gt 0 ]; then
    SELECTED_DEVICE="${API_35_DEVICES[0]}"
    echo ""
    echo "✓ Using API 35 device (preferred): $SELECTED_DEVICE"
    if [ ${#API_35_DEVICES[@]} -gt 1 ]; then
        echo "  (Note: ${#API_35_DEVICES[@]} API 35 devices available, using first one)"
    fi
else
    SELECTED_DEVICE="${COMPATIBLE_DEVICES[0]}"
    echo ""
    echo "✓ Using compatible device: $SELECTED_DEVICE [API $(get_api_level "$SELECTED_DEVICE")]"
    if [ ${#COMPATIBLE_DEVICES[@]} -gt 1 ]; then
        echo "  (Note: ${#COMPATIBLE_DEVICES[@]} compatible devices available, using first one)"
    fi
fi

echo ""
echo "Running tests on: $SELECTED_DEVICE"
echo ""

# Set ANDROID_SERIAL to limit Gradle to only the selected device
export ANDROID_SERIAL="$SELECTED_DEVICE"

# Run tests only on the selected compatible device
./gradlew connectedAndroidTest

# Unset ANDROID_SERIAL after tests complete
unset ANDROID_SERIAL

# Cleanup: kill emulator if we started it
if [ -n "$EMULATOR_PID" ]; then
    echo ""
    echo "Stopping emulator (PID: $EMULATOR_PID)..."
    kill $EMULATOR_PID 2>/dev/null || true
    # Wait a moment for graceful shutdown
    sleep 2
    # Force kill if still running
    kill -9 $EMULATOR_PID 2>/dev/null || true
fi

echo ""
echo "=== Tests completed ==="

