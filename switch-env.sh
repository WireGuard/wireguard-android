#!/bin/bash

# Check if exactly one argument is provided
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 {staging|production|local}"
    exit 1
fi

# Assign the first argument to the ENVIRONMENT variable
ENVIRONMENT=$1

# Validate the argument
if [[ "$ENVIRONMENT" != "staging" && "$ENVIRONMENT" != "production" && "$ENVIRONMENT" != "local" && "$ENVIRONMENT" != "dc" ]]; then
    echo "Error: Argument must be 'staging' or 'production' or 'local' or 'dc'."
    exit 1
fi

# Perform actions based on the environment
if [ "$ENVIRONMENT" = "staging" ]; then
    echo "Changing configs to the staging environment..."
    cp ./environments/staging/AndroidManifest.xml ./ui/src/main/AndroidManifest.xml
    cp ./environments/staging/Config.kt ./ui/src/main/java/com/jimberisolation/android/configStore
    cp ./environments/staging/msal_config.json ./ui/src/main/res/raw

    echo "staging" > ./environments/current_environment
    echo "Done"

elif [ "$ENVIRONMENT" = "production" ]; then
    echo "Changing to the production environment..."
    cp ./environments/production/AndroidManifest.xml ./ui/src/main/AndroidManifest.xml
    cp ./environments/production/Config.kt ./ui/src/main/java/com/jimberisolation/android/configStore
    cp ./environments/production/msal_config.json ./ui/src/main/res/raw

    echo "production" > ./environments/current_environment
    echo "Done"

elif [ "$ENVIRONMENT" = "local" ]; then
    echo "Changing to the local environment..."
    cp ./environments/local/AndroidManifest.xml ./ui/src/main/AndroidManifest.xml
    cp ./environments/local/Config.kt ./ui/src/main/java/com/jimberisolation/android/configStore
    cp ./environments/local/msal_config.json ./ui/src/main/res/raw

    echo "local" > ./environments/current_environment
    echo "Done"

elif [ "$ENVIRONMENT" = "dc" ]; then
    echo "Changing to the DC environment..."
    cp ./environments/dc/AndroidManifest.xml ./ui/src/main/AndroidManifest.xml
    cp ./environments/dc/Config.kt ./ui/src/main/java/com/jimberisolation/android/configStore
    cp ./environments/dc/msal_config.json ./ui/src/main/res/raw

    echo "dc" > ./environments/current_environment
    echo "Done"
fi
