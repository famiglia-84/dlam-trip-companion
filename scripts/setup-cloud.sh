#!/usr/bin/env bash
set -euo pipefail
trip_root=/workspace/.trip-toolchain
mkdir -p "$trip_root/downloads" "$trip_root/jdk" "$trip_root/android-sdk/cmdline-tools"
if [[ ! -x "$trip_root/jdk/bin/javac" ]]; then
    curl -fLsS --retry 2 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz' -o "$trip_root/downloads/jdk.tar.gz"
    printf '%s  %s\n' f2dc5418092c43003db8f9005c4a286e1c0104fea96ccdd49e8ebd037cac9219 "$trip_root/downloads/jdk.tar.gz" | sha256sum -c -
    tar -xzf "$trip_root/downloads/jdk.tar.gz" --strip-components=1 -C "$trip_root/jdk"
fi
if [[ ! -x "$trip_root/android-sdk/cmdline-tools/latest/bin/sdkmanager" ]]; then
    curl -fLsS --retry 2 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$trip_root/downloads/android-tools.zip"
    # Pinned checksum published in Google's repository2-1.xml for this exact archive.
    printf '%s  %s\n' 5fdcc763663eefb86a5b8879697aa6088b041e70 "$trip_root/downloads/android-tools.zip" | sha1sum -c -
    unzip -q "$trip_root/downloads/android-tools.zip" -d "$trip_root/android-sdk/cmdline-tools"
    mv "$trip_root/android-sdk/cmdline-tools/cmdline-tools" "$trip_root/android-sdk/cmdline-tools/latest"
fi
# Add the platform's public proxy CA to this local JDK's trust store; keep TLS verification enabled.
if [[ -n "${CODEX_PROXY_CERT:-}" && -f "$CODEX_PROXY_CERT" ]]; then
    if ! "$trip_root/jdk/bin/keytool" -list -cacerts -storepass changeit -alias codex-cloud-proxy >/dev/null 2>&1; then
        "$trip_root/jdk/bin/keytool" -importcert -noprompt -cacerts -storepass changeit -alias codex-cloud-proxy -file "$CODEX_PROXY_CERT"
    fi
fi
source "$(dirname "${BASH_SOURCE[0]}")/cloud-env.sh"
mkdir -p "$ANDROID_USER_HOME"
trip_sdk_args=()
if [[ -n "${trip_proxy_host:-}" ]]; then
    trip_sdk_args=(--proxy=http "--proxy_host=$trip_proxy_host" "--proxy_port=$trip_proxy_port")
fi
# Explicit license acceptance is needed for the Android build requested by the project brief.
# Capture the consumer's status; yes normally receives SIGPIPE when sdkmanager exits.
set +o pipefail
yes 2>/dev/null | sdkmanager "${trip_sdk_args[@]}" --licenses >/dev/null
trip_license_status=${PIPESTATUS[1]}
set -o pipefail
[[ "$trip_license_status" -eq 0 ]]
sdkmanager "${trip_sdk_args[@]}" 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
java -version
sdkmanager "${trip_sdk_args[@]}" --list_installed
