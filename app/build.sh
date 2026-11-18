#!/usr/bin/env bash
# Compila o APK sem Gradle, usando apenas o Android SDK (aapt2, javac, d8, zipalign, apksigner).
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
AJ="$SDK/platforms/android-36/android.jar"
BT="$SDK/build-tools/36.0.0"
[ -f "$AJ" ] || AJ="$SDK/platforms/android-37.0/android.jar"

rm -rf build && mkdir -p build/classes build/dex dist

mkdir -p build/assets
cp assets/* build/assets/
if [ -f ../mre/hello/MyMREApp.vxp ]; then
    cp ../mre/hello/MyMREApp.vxp build/assets/luizface.vxp
fi

javac --release 11 -Xlint:-options -classpath "$AJ" -d build/classes \
    src/com/luiz/w26/*.java

D8="$BT/d8.bat"
[ -f "$D8" ] || D8="$BT/d8"
"$D8" --lib "$AJ" --min-api 26 --output build/dex build/classes/com/luiz/w26/*.class

"$BT/aapt2" link -I "$AJ" --manifest AndroidManifest.xml \
    -A build/assets \
    --min-sdk-version 26 --target-sdk-version 36 \
    -o build/base.apk

python - "$PWD/build/base.apk" "$PWD/build/dex/classes.dex" "$PWD/build/unsigned.apk" <<'EOF'
import sys, zipfile, shutil
base, dex, out = sys.argv[1:4]
shutil.copy(base, out)
with zipfile.ZipFile(out, "a", zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
EOF

"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

if [ ! -f debug.keystore ]; then
    keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=W26 Bancada" >/dev/null 2>&1
fi

"$BT/apksigner.bat" sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out dist/w26.apk build/aligned.apk

echo "APK: $PWD/dist/w26.apk"
