#!/usr/bin/env bash
#
# Creates the release signing key for SoluPlay.
#
# The passphrase is read from the environment and handed to keytool through
# `:env`, so it never appears on a command line, in a log, or in this file.
# Nothing this script writes is committed: the keystore and the properties
# file are both in .gitignore.
#
#   export SOLUPLAY_KEYSTORE_PASSWORD='...'
#   scripts/create-release-keystore.sh
#
# The generated key is the one that signs every published APK, so it can
# never be replaced without breaking updates for people who already installed
# the app. Back it up to two places off this machine before you publish.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [ -z "${SOLUPLAY_KEYSTORE_PASSWORD:-}" ]; then
  echo "SOLUPLAY_KEYSTORE_PASSWORD is not set." >&2
  echo "Export it in this shell, then run this script again. It is never written to disk." >&2
  exit 1
fi

export SOLUPLAY_KEYSTORE_PASSWORD

# The toolchain lives in the account's home folder. The account's name is not
# written into this file, so the repository can be public.
USER_HOME="${USERPROFILE:-$HOME}"
JAVA_HOME="${JAVA_HOME:-$USER_HOME/android-tv-toolchain/jdk17/jdk-17.0.20.1+1}"
KEYTOOL="$JAVA_HOME/bin/keytool"
if [ ! -x "$KEYTOOL" ] && [ ! -f "$KEYTOOL.exe" ]; then
  echo "keytool not found at $KEYTOOL. Set JAVA_HOME to the JDK 17 folder." >&2
  exit 1
fi

ALIAS="${SOLUPLAY_KEY_ALIAS:-soluplay}"
VALIDITY_DAYS="${SOLUPLAY_KEY_VALIDITY_DAYS:-10950}"
KEYSTORE_DIR="$REPO_ROOT/keystore"
KEYSTORE_FILE="$KEYSTORE_DIR/release.jks"
PROPERTIES_FILE="$REPO_ROOT/keystore.properties"

if [ -f "$KEYSTORE_FILE" ]; then
  echo "$KEYSTORE_FILE already exists. A release key is created once and never replaced." >&2
  exit 1
fi

mkdir -p "$KEYSTORE_DIR"

"$KEYTOOL" -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity "$VALIDITY_DAYS" \
  -keystore "$KEYSTORE_FILE" \
  -storetype PKCS12 \
  -storepass:env SOLUPLAY_KEYSTORE_PASSWORD \
  -keypass:env SOLUPLAY_KEYSTORE_PASSWORD \
  -dname "CN=SoluPlay, OU=SoluPlay, O=SoluPlay, L=Dhaka, C=BD"

# The build reads these four names. The file is gitignored and holds the
# passphrase in plain text, so it never leaves this machine.
cat > "$PROPERTIES_FILE" <<EOF
storeFile=keystore/release.jks
storePassword=$SOLUPLAY_KEYSTORE_PASSWORD
keyAlias=$ALIAS
keyPassword=$SOLUPLAY_KEYSTORE_PASSWORD
EOF

echo
echo "Created $KEYSTORE_FILE (alias: $ALIAS, valid $VALIDITY_DAYS days)."
echo "Wrote $PROPERTIES_FILE for the Gradle build."
echo
echo "Back the keystore up to two places off this machine now, and keep the"
echo "passphrase in a password manager. Without both, no update can ever be"
echo "signed for the people who already installed the app."
