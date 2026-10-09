#!/usr/bin/env bash
# Source this file to activate the retained cloud toolchain. It does not launch processes.
export JAVA_HOME=/workspace/.trip-toolchain/jdk
export ANDROID_HOME=/workspace/.trip-toolchain/android-sdk
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_USER_HOME=/workspace/.trip-toolchain/android-user
export GRADLE_USER_HOME=/workspace/.trip-toolchain/gradle-cache
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
# Java tools do not consume the HTTPS_PROXY environment variable themselves.
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    read -r trip_proxy_host trip_proxy_port < <(node -e 'const u = new URL(process.env.HTTPS_PROXY); console.log(u.hostname, u.port || "80")')
    export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttps.proxyHost=$trip_proxy_host -Dhttps.proxyPort=$trip_proxy_port -Dhttp.proxyHost=$trip_proxy_host -Dhttp.proxyPort=$trip_proxy_port -Dhttp.nonProxyHosts=localhost\|127.*"
fi
