#!/usr/bin/env bash
set -e

APP_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_PLATFORM="${TARGET_PLATFORM:-auto}"
DEBUG_SUSPEND=n
REMOTE_DEBUG_MODE=false
APP_ARGS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --target|--platform)
            if [[ $# -lt 2 ]]; then echo "Missing target for $1" >&2; exit 2; fi
            TARGET_PLATFORM="$2"; shift 2 ;;
        --target=*|--platform=*) TARGET_PLATFORM="${1#*=}"; shift ;;
        --raspi|--aarch64) TARGET_PLATFORM=raspi; shift ;;
        --linux|--x64|--linux-x64) TARGET_PLATFORM=linux; shift ;;
        --remote-debug|--jdwp) REMOTE_DEBUG_MODE=true; shift ;;
        --debug-wait|--debug-suspend) REMOTE_DEBUG_MODE=true; DEBUG_SUSPEND=y; shift ;;
        *) APP_ARGS+=("$1"); shift ;;
    esac
done

if [[ "$TARGET_PLATFORM" == auto ]]; then
    case "$(uname -m)" in aarch64*|arm64*) TARGET_PLATFORM=raspi ;; *) TARGET_PLATFORM=linux ;; esac
fi
case "$TARGET_PLATFORM" in
    raspi*|linux-aarch64|aarch64) NATIVE_DIR="$APP_DIR/lib/native/aarch64" ;;
    linux*|x86_64|amd64) NATIVE_DIR="$APP_DIR/lib/native/linux-x64" ;;
    *) echo "Unsupported target: $TARGET_PLATFORM" >&2; exit 2 ;;
esac
if [[ ! -d "$NATIVE_DIR" ]]; then echo "Native library directory not found: $NATIVE_DIR" >&2; exit 1; fi
for libfile in "$NATIVE_DIR"/lib*.so.*.*.*; do
    if [[ -f "$libfile" && ! -L "$libfile" ]]; then
        basefile="$(basename "$libfile")"
        libminor="${basefile%.[0-9]*}"
        libmajor="${libminor%.[0-9]*}"
        libname="${libmajor%.[0-9]*}"
        for link in "$libmajor" "$libname"; do
            if [[ ! -e "$NATIVE_DIR/$link" ]]; then
                ln -s "$basefile" "$NATIVE_DIR/$link" 2>/dev/null || cp "$libfile" "$NATIVE_DIR/$link" 2>/dev/null || true
            fi
        done
    fi
done
export LD_LIBRARY_PATH="$NATIVE_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JVM_OPTS=("--enable-native-access=ALL-UNNAMED" "-Djava.library.path=$NATIVE_DIR")
if [[ "${REMOTE_DEBUG:-0}" == 1 || "${DEBUG:-0}" == 1 || "$REMOTE_DEBUG_MODE" == true ]]; then
    JVM_OPTS+=("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND,address=*:${DEBUG_PORT:-5005}")
fi
if [[ -n "${LOG_LEVEL:-}" ]]; then JVM_OPTS+=("-DLOG_LEVEL=$LOG_LEVEL"); fi
cd "$APP_DIR"
exec "$JAVA_CMD" "${JVM_OPTS[@]}" -cp "$APP_DIR/rfid-headless.jar:$APP_DIR/lib/*" org.objectspace.rfid.headless.HeadlessReader "${APP_ARGS[@]}"