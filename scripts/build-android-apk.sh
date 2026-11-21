#!/usr/bin/env bash
set -euo pipefail

# Repackage the official Local Dream arm64 release with Anime Image Studio branding.
# The upstream APK is checksum-pinned; its native AI engine and model manager are retained.
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK_DIR="${RUNNER_TEMP:-/tmp}/anime-image-studio-build"
APKTOOL_VERSION="3.0.3"
UPSTREAM_URL="https://github.com/xororz/local-dream/releases/download/v2.8.1/LocalDream_armv8a_2.8.1.apk"
UPSTREAM_SHA256="c8ba61769564ed13bcfb7e13cc48bb3bf6e3f9fd26db132a0bb2d6aa00a31edb"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/35.0.0"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

echo "Downloading official Local Dream arm64 release..."
curl --fail --location --retry 3 "$UPSTREAM_URL" -o upstream.apk
echo "$UPSTREAM_SHA256  upstream.apk" | sha256sum --check -

echo "Downloading Apktool $APKTOOL_VERSION..."
curl --fail --location --retry 3 \
  "https://github.com/iBotPeaches/Apktool/releases/download/v${APKTOOL_VERSION}/apktool_${APKTOOL_VERSION}.jar" \
  -o apktool.jar

java -jar apktool.jar d --force --output decoded upstream.apk

echo "Applying Anime Image Studio app label and attribution..."
python3 - "$WORK_DIR/decoded/res/values/strings.xml" <<'PY'
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
tree = ET.parse(path)
root = tree.getroot()
matches = [node for node in root.findall("string") if node.get("name") == "app_name"]
if len(matches) != 1:
    raise SystemExit(f"Expected exactly one app_name resource, found {len(matches)}")
matches[0].text = "Anime Image Studio"
tree.write(path, encoding="utf-8", xml_declaration=True)
PY

mkdir -p decoded/assets
cp "$ROOT_DIR/android/THIRD_PARTY_NOTICES.txt" decoded/assets/THIRD_PARTY_NOTICES.txt

java -jar apktool.jar b decoded --output unsigned.apk
"$BUILD_TOOLS/zipalign" -f -p 4 unsigned.apk aligned.apk

# A local-install signing key is generated for this build. A later build may
# require uninstalling the previous APK before updating because the key changes.
keytool -genkeypair -noprompt \
  -keystore anime-image-studio.keystore \
  -storepass android -keypass android \
  -alias anime-image-studio \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Anime Image Studio, O=Personal Use, C=IN"

"$BUILD_TOOLS/apksigner" sign \
  --ks anime-image-studio.keystore \
  --ks-key-alias anime-image-studio \
  --ks-pass pass:android --key-pass pass:android \
  --out "$ROOT_DIR/Anime-Image-Studio-arm64.apk" aligned.apk

"$BUILD_TOOLS/apksigner" verify --verbose "$ROOT_DIR/Anime-Image-Studio-arm64.apk"
"$BUILD_TOOLS/aapt" dump badging "$ROOT_DIR/Anime-Image-Studio-arm64.apk" | grep -E "^(package:|application-label:|native-code:)"
sha256sum "$ROOT_DIR/Anime-Image-Studio-arm64.apk"
ls -lh "$ROOT_DIR/Anime-Image-Studio-arm64.apk"
