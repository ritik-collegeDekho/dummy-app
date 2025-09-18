#!/usr/bin/env bash
set -euo pipefail
# ---------------------
# Safety and strictness:
# - `set -e` : exit if any command fails
# - `set -u` : exit if unbound variable used
# - `set -o pipefail` : fail if any command in a pipe fails
# This makes the script safer and stops on errors instead of silently continuing.
# ---------------------

# ----- CONFIG (edit these) -----
# The top of the script declares variables you may want to change for your project.
PROJECT_DIR="${1:-$(pwd)}"        # If you pass a path as first argument use it; otherwise use current working dir
APP_MODULE="app"                  # The Gradle module with your app (usually "app")
KEYSTORE="${HOME}/myapp-keystore.jks"  # path to keystore file (created if missing)
KEY_ALIAS="myapp_key"             # alias inside the keystore
STORE_PASS="changeit"             # keystore password (CHANGE for production)
KEY_PASS="changeit"               # key password (CHANGE for production)
PACKAGE_NAME="com.example.myapp"  # your app package (must match your manifest)
RECEIVER_NAME="MyDeviceAdminReceiver"  # class name of your DeviceAdminReceiver
ENROLLMENT_URL="https://your-mdm-server.com/enroll"  # sample extra to pass to DPC
DEVICE_ID="MYAPP-TEST-001"        # sample extra to pass to DPC
APK_HOST_URL="${APK_HOST_URL:-https://github.com/ritik-collegeDekho/dummy-app/releases/download/v1.0/app-release-signed.apk}" # placeholder URL for where the signed APK will be hosted (must be HTTPS)
HOST_DIR="$PROJECT_DIR/host"      # output directory for JSON + QR image
QR_FILE="$HOST_DIR/provision.png" # output QR png
JSON_FILE="$HOST_DIR/provision.json"
# -------------------

mkdir -p "$HOST_DIR"
# ensure output directory exists

# export SDK if not present:
if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
  export ANDROID_SDK_ROOT="${HOME}/Library/Android/sdk"
  echo "Set ANDROID_SDK_ROOT to $ANDROID_SDK_ROOT"
fi
# If user hasn't set ANDROID_SDK_ROOT in environment, default to the usual macOS path.

# find apksigner
if [[ -x "${ANDROID_SDK_ROOT}/build-tools/$(ls ${ANDROID_SDK_ROOT}/build-tools | tail -n1)/apksigner" ]]; then
  APKSIGNER="${ANDROID_SDK_ROOT}/build-tools/$(ls ${ANDROID_SDK_ROOT}/build-tools | tail -n1)/apksigner"
else
  APKSIGNER="$(command -v apksigner || true)"
fi

if [[ -z "$APKSIGNER" ]]; then
  echo "ERROR: apksigner not found. Install Android build-tools or add apksigner to PATH." >&2
  exit 1
fi
echo "Using apksigner: $APKSIGNER"
# This block attempts to locate `apksigner`:
# - first, find it in the latest Android build-tools in $ANDROID_SDK_ROOT
# - otherwise look for it in PATH
# apksigner is required to sign and inspect the APK; script fails if missing.

# Step 1: Build release APK
echo "Building release APK..."
cd "$PROJECT_DIR"
if [[ ! -f "./gradlew" ]]; then
  echo "gradlew not found in project root. Create project via Android Studio or ensure gradlew exists." >&2
  exit 1
fi
./gradlew clean assembleRelease
# Uses the Gradle wrapper to build a release APK. The wrapper ensures consistent Gradle version.

# locate produced APK
APK_DIR="$PROJECT_DIR/$APP_MODULE/build/outputs/apk/release"
UNSIGNED_APK="$(find "$APK_DIR" -maxdepth 1 -type f \( -name "*release-unsigned*.apk" -o -name "*release.apk" \) | head -n 1)"

if [[ -z "$UNSIGNED_APK" ]]; then
  echo "ERROR: release APK not found under $APK_DIR" >&2
  exit 1
fi
echo "Found APK: $UNSIGNED_APK"
# Looks for the release APK produced by Gradle. Fails if none found.

# Step 2: Create keystore if not exists
if [[ ! -f "$KEYSTORE" ]]; then
  echo "Keystore not found, creating: $KEYSTORE"
  keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STORE_PASS" -keypass "$KEY_PASS" \
    -dname "CN=MyApp, OU=Dev, O=MyCompany, L=City, ST=State, C=IN"
else
  echo "Using existing keystore: $KEYSTORE"
fi
# If your keystore doesn’t exist, this creates one using Java's keytool.
# Change -dname, passwords, and path for production; do NOT commit keystore to source control.

# Step 3: Sign the APK (copy then sign in-place)
SIGNED_APK="$APK_DIR/app-release-signed.apk"
cp "$UNSIGNED_APK" "$SIGNED_APK"
echo "Signing APK..."
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass pass:"$STORE_PASS" --key-pass pass:"$KEY_PASS" "$SIGNED_APK"

echo "Signed APK: $SIGNED_APK"
# Copies the unsigned APK to a new file and signs it with apksigner using the keystore and alias.
# Signed APK is required for devices to accept and install as DPC.

# Step 4: Compute certificate (signature) checksum (base64url of cert SHA-256)
echo "Computing signature checksum (base64url of cert SHA-256)..."
CERT_HEX="$("$APKSIGNER" verify --print-certs "$SIGNED_APK" 2>/dev/null | awk '/SHA-256/{print $NF; exit}')"
if [[ -z "$CERT_HEX" ]]; then
  # fallback: try keytool
  echo "apksigner did not print cert SHA-256; trying keytool -printcert -jarfile..."
  CERT_HEX="$(keytool -printcert -jarfile "$SIGNED_APK" 2>/dev/null | awk '/SHA256/{print $NF; exit}')"
fi
CERT_HEX="$(echo "$CERT_HEX" | tr -d ':')"
if [[ -z "$CERT_HEX" ]]; then
  echo "ERROR: couldn't extract cert SHA-256 hex. Please check apksigner/keytool output." >&2
  exit 1
fi
SIGNATURE_CHECKSUM="$(echo "$CERT_HEX" | xxd -r -p | openssl base64 | tr '+/' '-_' | tr -d '=')"
echo "Signature checksum: $SIGNATURE_CHECKSUM"
# Explanation:
# - `apksigner verify --print-certs` prints certificate fingerprints including SHA-256 in hex with ":" separators.
# - awk extracts the hex fingerprint.
# - remove colons so we have a continuous hex string.
# - xxd -r -p converts hex to raw bytes.
# - openssl base64 encodes raw bytes to standard base64.
# - tr '+/' '-_' converts to URL-safe base64, and tr -d '=' removes base64 padding.
# The final string matches Android's expected base64url signature-checksum field.

# Step 5: Compute package checksum (APK SHA-256 base64url)
echo "Computing package checksum (APK SHA-256 base64url)..."
PACKAGE_CHECKSUM="$(openssl dgst -binary -sha256 "$SIGNED_APK" | openssl base64 | tr '+/' '-_' | tr -d '=')"
echo "Package checksum: $PACKAGE_CHECKSUM"
# This computes SHA-256 of the full APK bytes (useful if you prefer to pin to exact APK file instead of signature).

# Step 6: Create compact JSON for QR (uses signature checksum by default)
COMPONENT="${PACKAGE_NAME}/.${RECEIVER_NAME}"
cat > "$JSON_FILE" <<EOF
{"android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME":"${COMPONENT}","android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM":"${SIGNATURE_CHECKSUM}","android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION":"${APK_HOST_URL}","android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE":{"enrollment_url":"${ENROLLMENT_URL}","device_id":"${DEVICE_ID}"}}
EOF

echo "Wrote provisioning JSON to: $JSON_FILE"
echo "JSON contents:"
cat "$JSON_FILE"
echo
# Writes the compact (no spaces/newlines) JSON. Important: the scanner expects raw JSON payload (no extra escaping).
# NOTE: Update APK_HOST_URL to the real HTTPS location where the device can download the APK.

# Step 7: Generate QR image from JSON (python)
echo "Generating QR PNG..."
python3 - <<PY
import json,sys
try:
    import qrcode
except Exception as e:
    print("Please install Python qrcode and pillow: pip3 install --user qrcode[pil] pillow", file=sys.stderr)
    raise
data = json.load(open("$JSON_FILE"))
payload = json.dumps(data, separators=(',',':'))
img = qrcode.make(payload)
img.save("$QR_FILE")
print("QR written to: $QR_FILE")
PY

echo
echo "DONE."
echo
echo "IMPORTANT NEXT STEPS:"
echo "  1) Host the signed APK ($SIGNED_APK) at an HTTPS URL and set APK_HOST_URL to that HTTPS URL"
echo "  2) Update the APK_HOST_URL placeholder inside $JSON_FILE (or set the env var and re-run script)"
echo "  3) To test without OOBE you can use adb (dev only):"
echo "       adb install -r '$SIGNED_APK'"
echo "       adb shell dpm set-device-owner \"${PACKAGE_NAME}/.${RECEIVER_NAME}\""
echo
echo "Files created in: $HOST_DIR"
ls -l "$HOST_DIR"