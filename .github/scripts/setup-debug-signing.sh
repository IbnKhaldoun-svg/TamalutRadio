#!/usr/bin/env bash
set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN is required to download the private debug signing asset}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"

SIGNING_DIR="$RUNNER_TEMP/tamalut-debug-signing-v1"
KEYSTORE="$SIGNING_DIR/tamalut-debug.keystore"
EXPECTED_KEYSTORE_SHA="$(tr -d '[:space:]' < .github/signing/debug-keystore-v1.sha256)"
EXPECTED_CERT_SHA="$(tr -d '[:space:]' < .github/signing/debug-cert-v1.sha256)"

mkdir -p "$SIGNING_DIR"
rm -f "$KEYSTORE"
gh release download debug-signing-key-v1 \
  --repo "$GITHUB_REPOSITORY" \
  --pattern tamalut-debug.keystore \
  --dir "$SIGNING_DIR"
chmod 600 "$KEYSTORE"

ACTUAL_KEYSTORE_SHA="$(sha256sum "$KEYSTORE" | awk '{print $1}')"
if [[ "$ACTUAL_KEYSTORE_SHA" != "$EXPECTED_KEYSTORE_SHA" ]]; then
  echo "Persistent debug keystore checksum mismatch" >&2
  exit 1
fi

ACTUAL_CERT_SHA="$(keytool -exportcert -keystore "$KEYSTORE" -storepass android -alias androiddebugkey -rfc \
  | openssl x509 -noout -fingerprint -sha256 \
  | sed 's/^sha256 Fingerprint=//;s/://g' \
  | tr '[:upper:]' '[:lower:]')"
if [[ "$ACTUAL_CERT_SHA" != "$EXPECTED_CERT_SHA" ]]; then
  echo "Persistent debug signing certificate mismatch" >&2
  exit 1
fi

{
  echo "TAMALUT_DEBUG_KEYSTORE_PATH=$KEYSTORE"
  echo "TAMALUT_DEBUG_KEYSTORE_PASSWORD=android"
  echo "TAMALUT_DEBUG_KEY_ALIAS=androiddebugkey"
  echo "TAMALUT_DEBUG_KEY_PASSWORD=android"
} >> "$GITHUB_ENV"

echo "Persistent debug signing ready: cert SHA-256 $ACTUAL_CERT_SHA"
