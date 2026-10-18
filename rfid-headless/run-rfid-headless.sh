#!/usr/bin/env bash
# ========================================================
#   RFID Headless - Headless Continuous RFID Daemon
#   Multi-Platform Execution Script (Bash)
#   Target: Linux (x64), Raspberry Pi (aarch64), Windows
#   info-age GmbH, Basel
# ========================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Detect base application directory (standalone dist vs module vs project root)
if [ -d "$SCRIPT_DIR/lib" ]; then
    APP_DIR="$SCRIPT_DIR"
    PROJECT_ROOT="$SCRIPT_DIR"
elif [ -d "$SCRIPT_DIR/rfid-headless" ]; then
    APP_DIR="$SCRIPT_DIR/rfid-headless"
    PROJECT_ROOT="$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/pom.xml" ] && [ -d "$SCRIPT_DIR/src" ]; then
    APP_DIR="$SCRIPT_DIR"
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
else
    APP_DIR="$SCRIPT_DIR"
    PROJECT_ROOT="$SCRIPT_DIR"
fi

# Detect platform / architecture or parse from flags
TARGET_PLATFORM="${TARGET_PLATFORM:-auto}"
REMOTE_DEBUG_MODE=false
DEBUG_SUSPEND=n
DEBUG_PORT="${DEBUG_PORT:-5005}"
APP_ARGS=()

while [[ $# -gt 0 ]]; do
    case "$1" in
        --target|--platform)
            TARGET_PLATFORM="$2"
            shift 2
            ;;
        --target=*|--platform=*)
            TARGET_PLATFORM="${1#*=}"
            shift
            ;;
        --raspi|--aarch64)
            TARGET_PLATFORM="raspi"
            shift
            ;;
        --linux|--x64|--linux-x64)
            TARGET_PLATFORM="linux"
            shift
            ;;
        --windows|--win)
            TARGET_PLATFORM="windows"
            shift
            ;;
        --remote-debug|--jdwp)
            REMOTE_DEBUG_MODE=true
            shift
            ;;
        --debug-wait|--debug-suspend)
            REMOTE_DEBUG_MODE=true
            DEBUG_SUSPEND=y
            shift
            ;;
        *)
            APP_ARGS+=("$1")
            shift
            ;;
    esac
done

if [ "$TARGET_PLATFORM" = "auto" ]; then
    UNAME_S="$(uname -s 2>/dev/null || echo "")"
    UNAME_M="$(uname -m 2>/dev/null || echo "")"
    case "$UNAME_S" in
        CYGWIN*|MINGW*|MSYS*)
            TARGET_PLATFORM="windows"
            ;;
        *)
            case "$UNAME_M" in
                aarch64*|arm64*|armv*)
                    TARGET_PLATFORM="raspi"
                    ;;
                *)
                    TARGET_PLATFORM="linux"
                    ;;
            esac
            ;;
    esac
fi

# Normalize TARGET_PLATFORM
case "$TARGET_PLATFORM" in
    raspi*|linux-aarch64|aarch64)
        TARGET_PLATFORM="raspi"
        ;;
    linux*|x86_64|amd64)
        TARGET_PLATFORM="linux"
        ;;
    win*|windows*)
        TARGET_PLATFORM="windows"
        ;;
esac

# Locate Native Libraries based on target
NATIVE_DIR=""
if [ "$TARGET_PLATFORM" = "raspi" ]; then
    if [ -d "$APP_DIR/lib/native/aarch64" ]; then
        NATIVE_DIR="$APP_DIR/lib/native/aarch64"
    elif [ -d "$APP_DIR/native/aarch64" ]; then
        NATIVE_DIR="$APP_DIR/native/aarch64"
    elif [ -d "$PROJECT_ROOT/lib/native/aarch64" ]; then
        NATIVE_DIR="$PROJECT_ROOT/lib/native/aarch64"
    elif [ -d "$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0/java/bin/release" ]; then
        NATIVE_DIR="$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0/java/bin/release"
    elif [ -d "$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0/cpp/bin/release" ]; then
        NATIVE_DIR="$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0/cpp/bin/release"
    fi
elif [ "$TARGET_PLATFORM" = "linux" ]; then
    if [ -d "$APP_DIR/lib/native/linux-x64" ]; then
        NATIVE_DIR="$APP_DIR/lib/native/linux-x64"
    elif [ -d "$APP_DIR/lib/native/x64" ]; then
        NATIVE_DIR="$APP_DIR/lib/native/x64"
    elif [ -d "$APP_DIR/native/linux-x64" ]; then
        NATIVE_DIR="$APP_DIR/native/linux-x64"
    elif [ -d "$PROJECT_ROOT/lib/native/linux-x64" ]; then
        NATIVE_DIR="$PROJECT_ROOT/lib/native/linux-x64"
    elif [ -d "$PROJECT_ROOT/lib/native/x64" ]; then
        NATIVE_DIR="$PROJECT_ROOT/lib/native/x64"
    elif [ -d "$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Java-v7.1.0/linux.x64/bin" ]; then
        NATIVE_DIR="$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Java-v7.1.0/linux.x64/bin"
    fi
elif [ "$TARGET_PLATFORM" = "windows" ]; then
    if [ -d "$APP_DIR/lib/native/x64" ]; then
        NATIVE_DIR="$APP_DIR/lib/native/x64"
    elif [ -d "$PROJECT_ROOT/lib/native/x64" ]; then
        NATIVE_DIR="$PROJECT_ROOT/lib/native/x64"
    elif [ -d "$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Java-v7.1.0/windows.x64/bin" ]; then
        NATIVE_DIR="$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Java-v7.1.0/windows.x64/bin"
    fi
fi

if [ -n "$NATIVE_DIR" ] && [ -d "$NATIVE_DIR" ]; then
    # Ensure unversioned and major version .so symlinks exist for Java JNI System.loadLibrary on Unix
    if [ "$TARGET_PLATFORM" != "windows" ]; then
        for libfile in "$NATIVE_DIR"/lib*.so.*.*.*; do
            if [ -f "$libfile" ] && [ ! -L "$libfile" ]; then
                basefile="$(basename "$libfile")"
                libminor="${basefile%.[0-9]*}"
                libmajor="${libminor%.[0-9]*}"
                libname="${libmajor%.[0-9]*}"
                if [ -n "$libmajor" ] && [ ! -e "$NATIVE_DIR/$libmajor" ]; then
                    ln -sf "$basefile" "$NATIVE_DIR/$libmajor" 2>/dev/null || cp "$libfile" "$NATIVE_DIR/$libmajor" 2>/dev/null || true
                fi
                if [ -n "$libname" ] && [ ! -e "$NATIVE_DIR/$libname" ]; then
                    ln -sf "$basefile" "$NATIVE_DIR/$libname" 2>/dev/null || cp "$libfile" "$NATIVE_DIR/$libname" 2>/dev/null || true
                fi
            fi
        done
        export LD_LIBRARY_PATH="$NATIVE_DIR:${LD_LIBRARY_PATH:-}"
    else
        export PATH="$NATIVE_DIR:${PATH:-}"
    fi
    echo "[RFID-Headless] Target: $TARGET_PLATFORM | Native library directory: $NATIVE_DIR"
else
    echo "[RFID-Headless] WARNING: Native library directory for target '$TARGET_PLATFORM' not found!" >&2
fi

# Construct Classpath
CLASSPATH=""

# 1. Main Application JAR & Core JAR in standalone dist
if [ -f "$APP_DIR/rfid-headless.jar" ]; then
    CLASSPATH="$APP_DIR/rfid-headless.jar"
fi
if [ -f "$APP_DIR/lib/rfid-headless.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/lib/rfid-headless.jar"
fi
if [ -f "$APP_DIR/lib/rfid-core.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/lib/rfid-core.jar"
fi
if [ -f "$APP_DIR/lib/certmanager.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/lib/certmanager.jar"
fi
if [ -f "$APP_DIR/lib/wsbrokerclient.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/lib/wsbrokerclient.jar"
fi

# 2. Development target classes or JARs
if [ -d "$APP_DIR/target/classes" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/target/classes"
fi
if [ -d "$PROJECT_ROOT/rfid-core/target/classes" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/rfid-core/target/classes"
fi
if [ -d "$PROJECT_ROOT/certmanager/target/classes" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/certmanager/target/classes"
fi
if [ -d "$PROJECT_ROOT/wsbrokerclient/target/classes" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/wsbrokerclient/target/classes"
fi
if [ -f "$APP_DIR/target/rfid-headless-2.0.0-SNAPSHOT.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/target/rfid-headless-2.0.0-SNAPSHOT.jar"
fi
if [ -f "$PROJECT_ROOT/rfid-core/target/rfid-core-2.0.0-SNAPSHOT.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/rfid-core/target/rfid-core-2.0.0-SNAPSHOT.jar"
fi
if [ -f "$PROJECT_ROOT/certmanager/target/certmanager-2.0.0-SNAPSHOT.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/certmanager/target/certmanager-2.0.0-SNAPSHOT.jar"
fi
if [ -f "$PROJECT_ROOT/wsbrokerclient/target/wsbrokerclient-2.0.0-SNAPSHOT.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/wsbrokerclient/target/wsbrokerclient-2.0.0-SNAPSHOT.jar"
fi

# 3. Add bundled JARs from lib/ and lib/ext/
if [ -d "$APP_DIR/lib" ]; then
    for jar in "$APP_DIR"/lib/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="${CLASSPATH:+$CLASSPATH:}$jar"
        fi
    done
fi
if [ -d "$APP_DIR/lib/ext" ]; then
    for jar in "$APP_DIR"/lib/ext/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="${CLASSPATH:+$CLASSPATH:}$jar"
        fi
    done
fi

# 4. Project root lib fallback for development runs
if [ -d "$PROJECT_ROOT/lib" ] && [ "$PROJECT_ROOT/lib" != "$APP_DIR/lib" ]; then
    for jar in "$PROJECT_ROOT"/lib/*.jar "$PROJECT_ROOT"/lib/ext/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="${CLASSPATH:+$CLASSPATH:}$jar"
        fi
    done
fi

# 5. Maven local repository fallback if running in dev environment
M2_REPO="${HOME}/.m2/repository"
if [ -d "$M2_REPO" ]; then
    for jar in $(find "$M2_REPO" -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" 2>/dev/null | grep -E "commons-|slf4j-|logback-|mysql-|jackson-|bcprov-|bcpkix-|bcutil-" || true); do
        if [ -f "$jar" ]; then
            CLASSPATH="${CLASSPATH:+$CLASSPATH:}$jar"
        fi
    done
fi

JAVA_CMD="${JAVA_HOME:+$JAVA_HOME/bin/}java"
if ! command -v "$JAVA_CMD" &> /dev/null; then
    JAVA_CMD="java"
fi

JVM_OPTS=(
    "--enable-native-access=ALL-UNNAMED"
)
if [ -n "$NATIVE_DIR" ]; then
    JVM_OPTS+=("-Djava.library.path=$NATIVE_DIR")
fi

# Process debug flags and arguments
REMOTE_DEBUG_MODE=false
DEBUG_SUSPEND=n
DEBUG_PORT="${DEBUG_PORT:-5005}"
APP_ARGS=()

for arg in "$@"; do
    case "$arg" in
        --remote-debug|--jdwp)
            REMOTE_DEBUG_MODE=true
            ;;
        --debug-wait|--debug-suspend)
            REMOTE_DEBUG_MODE=true
            DEBUG_SUSPEND=y
            ;;
        *)
            APP_ARGS+=("$arg")
            ;;
    esac
done

if [ "${REMOTE_DEBUG:-0}" = "1" ] || [ "${DEBUG:-0}" = "1" ] || [ "$REMOTE_DEBUG_MODE" = true ]; then
    echo "[RFID-Headless] Enabling JVM Remote Debugging on port $DEBUG_PORT (suspend=$DEBUG_SUSPEND)..."
    JVM_OPTS+=("-agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND,address=*:$DEBUG_PORT")
fi

if [ -n "$LOG_LEVEL" ]; then
    JVM_OPTS+=("-DLOG_LEVEL=$LOG_LEVEL")
fi

# Default configuration argument if none provided and inventory.xml exists
DEFAULT_CONFIG_ARGS=()
HAS_CONFIG_ARG=false
for ((i=0; i<${#APP_ARGS[@]}; i++)); do
    arg="${APP_ARGS[i]}"
    if [ "$arg" = "-c" ] || [ "$arg" = "--config" ] || [ "$arg" = "-h" ] || [ "$arg" = "--help" ] || [ "$arg" = "-v" ] || [ "$arg" = "--version" ]; then
        HAS_CONFIG_ARG=true
        break
    elif [[ "$arg" != -* ]] && [[ "$arg" == *.xml ]]; then
        HAS_CONFIG_ARG=true
        break
    fi
done

if [ "$HAS_CONFIG_ARG" = false ]; then
    if [ -f "$APP_DIR/inventory.xml" ]; then
        DEFAULT_CONFIG_ARGS=("-c" "$APP_DIR/inventory.xml")
    elif [ -f "$PROJECT_ROOT/inventory.xml" ]; then
        DEFAULT_CONFIG_ARGS=("-c" "$PROJECT_ROOT/inventory.xml")
    fi
fi

echo "[RFID-Headless] Starting headless reader daemon..."
exec "$JAVA_CMD" "${JVM_OPTS[@]}" -cp "$CLASSPATH" org.objectspace.rfid.headless.HeadlessReader "${DEFAULT_CONFIG_ARGS[@]}" "${APP_ARGS[@]}"
