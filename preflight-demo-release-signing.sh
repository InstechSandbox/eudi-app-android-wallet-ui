#!/bin/bash

set -euo pipefail

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_DIR="$SCRIPT_DIR"
SIGN_FILE="$REPO_DIR/sign"
LOCAL_SIGNING_FILE="$REPO_DIR/local.signing.properties"
KEY_ALIAS="${ANDROID_KEY_ALIAS:-}"
KEY_PASSWORD="${ANDROID_KEY_PASSWORD:-}"

read_property() {
  local file_path="$1"
  local key="$2"

  python3 - "$file_path" "$key" <<'PY'
import pathlib
import sys

file_path = pathlib.Path(sys.argv[1])
key = sys.argv[2]

if not file_path.exists():
    raise SystemExit(1)

for raw_line in file_path.read_text(encoding="utf-8").splitlines():
    line = raw_line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    current_key, value = line.split("=", 1)
    if current_key.strip() == key:
        print(value.strip())
        raise SystemExit(0)

raise SystemExit(1)
PY
}

if [[ -z "$KEY_ALIAS" && -f "$LOCAL_SIGNING_FILE" ]]; then
  KEY_ALIAS="$(read_property "$LOCAL_SIGNING_FILE" androidKeyAlias || true)"
fi

if [[ -z "$KEY_PASSWORD" && -f "$LOCAL_SIGNING_FILE" ]]; then
  KEY_PASSWORD="$(read_property "$LOCAL_SIGNING_FILE" androidKeyPassword || true)"
fi

if [[ ! -f "$SIGN_FILE" ]]; then
  printf '[fail] Missing keystore: %s\n' "$SIGN_FILE" >&2
  exit 1
fi

if [[ -z "$KEY_ALIAS" || -z "$KEY_PASSWORD" ]]; then
  printf '[fail] Missing release signing inputs.\n' >&2
  printf 'Create %s from %s or export ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD.\n' \
    "$LOCAL_SIGNING_FILE" "$REPO_DIR/local.signing.properties.example" >&2
  exit 1
fi

if ! keytool -list -keystore "$SIGN_FILE" -storepass "$KEY_PASSWORD" -alias "$KEY_ALIAS" >/dev/null 2>&1; then
  printf '[fail] The release alias/password do not match %s.\n' "$SIGN_FILE" >&2
  printf 'Either update %s or replace the local sign keystore before running demoRelease.\n' \
    "$LOCAL_SIGNING_FILE" >&2
  exit 1
fi

printf '[ok]   release signing inputs resolved\n'
printf '[ok]   release keystore matches alias/password\n'
printf 'Next step: ./gradlew :app:assembleDemoRelease --console=plain\n'