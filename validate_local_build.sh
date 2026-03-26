#!/bin/sh

set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$repo_dir"

gradle_user_home=${GRADLE_USER_HOME:-$repo_dir/.gradle-validate}
wallet_variant=${WALLET_VARIANT:-DevDebug}
local_properties_file=${LOCAL_PROPERTIES_FILE:-$repo_dir/local.properties}

detect_android_sdk() {
	if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
		printf '%s\n' "$ANDROID_SDK_ROOT"
		return
	fi

	if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
		printf '%s\n' "$ANDROID_HOME"
		return
	fi

	if [ -d "$HOME/Library/Android/sdk" ]; then
		printf '%s\n' "$HOME/Library/Android/sdk"
		return
	fi

	printf 'Android SDK not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.\n' >&2
	exit 1
}

android_sdk_dir=$(detect_android_sdk)

printf 'sdk.dir=%s\n' "$android_sdk_dir" > "$local_properties_file"

export GRADLE_USER_HOME="$gradle_user_home"

./gradlew --no-daemon workspaceClean ":app:assemble${wallet_variant}"

wallet_variant_path=$(printf '%s' "$wallet_variant" | sed 's/\([A-Z]\)/ \1/g' | awk '{for (i = 1; i <= NF; i++) printf "%s%s", (i > 1 ? "/" : ""), tolower($i)}')
wallet_apk=$(find "$repo_dir/app/build/outputs/apk/$wallet_variant_path" -type f -name '*.apk' | head -n 1)

if [ -z "$wallet_apk" ] || [ ! -s "$wallet_apk" ]; then
	printf 'Wallet smoke test failed: assembled APK for %s was not found\n' "$wallet_variant" >&2
	exit 1
fi

printf 'Validated wallet variant %s with artifact smoke test using GRADLE_USER_HOME=%s\n' "$wallet_variant" "$GRADLE_USER_HOME"