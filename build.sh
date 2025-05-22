#!/bin/bash

# Variables
TELEGRAM_BOT_TOKEN="868129294:AAEd-UDDSru9zGeGklzWL6mPO33NovuXYqo"
TELEGRAM_CHAT_ID="-1001186043363"

APK_PATH="./ui/build/outputs/apk/release/ui-release.apk"
SANITZED_APK_PATH="./ui/build/outputs/apk/release/jimber-network-isolation.apk"

PROJECT_DIR="$(pwd)"
BUILD_LOG="build.log"

GIT_USER=$(git config user.name)
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
GIT_HASH=$(git log --pretty=format:'%h' -n 1)

CURRENT_TIME=$(date "+%H:%M:%S %d.%m.%Y")

# ================
# Argument Handling
# ================

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 {staging|production|local|dc}"
    exit 1
fi

ENVIRONMENT=$1
if [[ "$ENVIRONMENT" != "staging" && "$ENVIRONMENT" != "production" && "$ENVIRONMENT" != "local" && "$ENVIRONMENT" != "dc" ]]; then
    echo "Error: Argument must be 'staging', 'production', or 'local' or 'dc'."
    exit 1
fi

./switch-env.sh "$ENVIRONMENT"

# ========================
# Telegram Message Function
# ========================

send_telegram_message() {
    local MESSAGE="$1"

    curl --http1.1 -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" \
        -d chat_id="$TELEGRAM_CHAT_ID" \
        -d parse_mode=markdown \
        -d text="App: *JimberNetworkIsolation*%0AType: *$ENVIRONMENT*%0AGit user: *$GIT_USER*%0AGit branch: *$GIT_BRANCH*%0AGit hash: *$GIT_HASH*%0ATime: *$CURRENT_TIME*%0AMessage: *$MESSAGE*"

    curl --http1.1 -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendDocument" \
        -F chat_id="$TELEGRAM_CHAT_ID" \
        -F document="@$SANITZED_APK_PATH"
}

# ============
# Build Process
# ============

echo "Cleaning previous builds..."
./gradlew clean

echo "Removing stale APK if it exists..."
rm -f "$APK_PATH" "$SANITZED_APK_PATH"

echo "Starting APK build..."
if ./gradlew assembleRelease > "$BUILD_LOG" 2>&1; then
    if [ -f "$APK_PATH" ]; then
        if [ ! -s "$APK_PATH" ]; then
            echo "Error: APK file is empty."
            send_telegram_message "❌ APK exists but is empty or corrupt."
            exit 1
        fi

        mv -f "$APK_PATH" "$SANITZED_APK_PATH"
        send_telegram_message "✅ Hoera, er is een nieuwe build beschikbaar!"
        rm -f "$SANITZED_APK_PATH"
    else
        echo "Error: APK not found after successful build."
        send_telegram_message "❌ Build succeeded but APK not found!"
        exit 1
    fi
else
    echo "Build failed. Check $BUILD_LOG for details."
    send_telegram_message "❌ Build failed. Check logs!"
    exit 1
fi
