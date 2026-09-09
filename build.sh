#!/bin/bash

set -e

cd TLPE
echo "######### Preparing to build... #########"

./gradlew clean
mkdir -p app/src/poc/assets/
rm -f app/src/poc/assets/system.apk

echo "######### Building system APK... #########"
./gradlew :app:assembleSystemRelease

SYSTEM_APK_PATH="app/build/outputs/apk/system/release/app-system-release.apk"

if [ -f "$SYSTEM_APK_PATH" ]; then
    echo "######### Copying System APK to assets... #########"
    cp "$SYSTEM_APK_PATH" "app/src/poc/assets/system.apk"
else
    echo "######### ERROR: System APK not found at $SYSTEM_APK_PATH #########"
    exit 1
fi

echo "######### Building PoC... #########"
./gradlew :app:assemblePocRelease

cd ..
FINAL_APK_PATH="TLPE/app/build/outputs/apk/poc/release/app-poc-release.apk"
cp "$FINAL_APK_PATH" "./poc.apk"

echo "######### Build finished: $(pwd)/poc.apk #########"

rm TLPE/app/src/poc/assets/system.apk
