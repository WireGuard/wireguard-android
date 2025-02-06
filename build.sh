#!/bin/bash

# Variables
TELEGRAM_BOT_TOKEN="868129294:AAEd-UDDSru9zGeGklzWL6mPO33NovuXYqo"
TELEGRAM_CHAT_ID="-1001186043363"

APK_PATH="./ui/build/outputs/apk/release/ui-release-unsigned.apk"
SANITZED_APK_PATH="./ui/build/outputs/apk/release/jimberfw.apk"

PROJECT_DIR="$(pwd)"
BUILD_LOG="build.log"

GIT_USER=$(git config user.name)
GIT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
GIT_HASH=$(git log --pretty=format:'%h' -n 1)

CURRENT_TIME=$(date "+%H:%M:%S %d.%m.%Y")

# Check if exactly one argument is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 {staging|production|local}"
    exit 1
fi

# Assign the first argument to the ENVIRONMENT variable
ENVIRONMENT=$1

# Validate the argument
if [[ "$ENVIRONMENT" != "staging" && "$ENVIRONMENT" != "production" && "$ENVIRONMENT" != "local" ]]; then
    echo "Error: Argument must be 'staging' or 'production' or 'local'."
    exit 1
fi

./switch-env.sh "$ENVIRONMENT"

# Function to send messages to Telegram
send_telegram_message() {
    local MESSAGE="$1"

    curl --http1.1 -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendMessage" -d parse_mode=markdown -d chat_id=$TELEGRAM_CHAT_ID -d parse_mode=markdown -d text="App: *JimberNetworkIsolation* %0AType: *$ENVIRONMENT* %0AGit user: *$GIT_USER* %0AGit branch: *$GIT_BRANCH* %0AGit hash: *$GIT_HASH* %0ATime: *$CURRENT_TIME* %0AMessage: *$MESSAGE*"
    curl --http1.1 -s -X POST "https://api.telegram.org/bot$TELEGRAM_BOT_TOKEN/sendDocument" -F chat_id=$TELEGRAM_CHAT_ID -F document="@$SANITZED_APK_PATH"
}

# Build the APK
./gradlew assembleRelease > "$BUILD_LOG" 2>&1

mv $APK_PATH $SANITZED_APK_PATH

# Check if build succeeded
if [ -f "$SANITZED_APK_PATH" ]; then    
    send_telegram_message "Hoera, er is een nieuwe build beschikbaar!"
else
    echo "Could not build, please check build.log"
fi
