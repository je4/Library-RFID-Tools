#!/usr/bin/env bash
# ========================================================
#   Raspi Inventory - Headless Continuous RFID Daemon
#   Target Architecture: Linux aarch64 (Raspberry Pi 4 / 5)
#   info-age GmbH, Basel
# ========================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Detect base application directory (standalone dist vs module vs project root)
if [ -d "$SCRIPT_DIR/lib" ]; then
    APP_DIR="$SCRIPT_DIR"
    PROJECT_ROOT="$SCRIPT_DIR"
elif [ -d "$SCRIPT_DIR/raspi-inventory" ]; then
    APP_DIR="$SCRIPT_DIR/raspi-inventory"
    PROJECT_ROOT="$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/pom.xml" ] && [ -d "$SCRIPT_DIR/src" ]; then
    APP_DIR="$SCRIPT_DIR"
    PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
else
    APP_DIR="$SCRIPT_DIR"
    PROJECT_ROOT="$SCRIPT_DIR"
fi

# Locate Native Libraries (aarch64)
NATIVE_DIR=""
if [ -d "$APP_DIR/lib/native/aarch64" ]; then
    NATIVE_DIR="$APP_DIR/lib/native/aarch64"
elif [ -d "$APP_DIR/native/aarch64" ]; then
    NATIVE_DIR="$APP_DIR/native/aarch64"
elif [ -d "$PROJECT_ROOT/lib/native/aarch64" ]; then
    NATIVE_DIR="$PROJECT_ROOT/lib/native/aarch64"
elif [ -d "$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0/java/bin/release" ]; then
    NATIVE_DIR="$PROJECT_ROOT/FEIG.ID.SDK.Gen3.Raspi.aarch64-v7.1.0/java/bin/release"
fi

if [ -n "$NATIVE_DIR" ] && [ -d "$NATIVE_DIR" ]; then
    # Ensure unversioned and major version .so symlinks exist for Java JNI System.loadLibrary
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
    echo "[Raspi-Inventory] Using native library directory: $NATIVE_DIR"
else
    echo "[Raspi-Inventory] WARNING: Native aarch64 library directory not found!" >&2
fi

# Construct Classpath
CLASSPATH=""

# 1. Main Application JAR & Core JAR in standalone dist
if [ -f "$APP_DIR/raspi-inventory.jar" ]; then
    CLASSPATH="$APP_DIR/raspi-inventory.jar"
fi
if [ -f "$APP_DIR/lib/raspi-inventory.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/lib/raspi-inventory.jar"
fi
if [ -f "$APP_DIR/lib/rfid-core.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/lib/rfid-core.jar"
fi

# 2. Development target classes or JARs
if [ -d "$APP_DIR/target/classes" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/target/classes"
fi
if [ -d "$PROJECT_ROOT/rfid-core/target/classes" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/rfid-core/target/classes"
fi
if [ -f "$APP_DIR/target/raspi-inventory-2.0.0-SNAPSHOT.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$APP_DIR/target/raspi-inventory-2.0.0-SNAPSHOT.jar"
fi
if [ -f "$PROJECT_ROOT/rfid-core/target/rfid-core-2.0.0-SNAPSHOT.jar" ]; then
    CLASSPATH="${CLASSPATH:+$CLASSPATH:}$PROJECT_ROOT/rfid-core/target/rfid-core-2.0.0-SNAPSHOT.jar"
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
    for jar in $(find "$M2_REPO" -name "*.jar" ! -name "*-sources.jar" ! -name "*-javadoc.jar" 2>/dev/null | grep -E "commons-|slf4j-|logback-|mysql-" || true); do
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

# Default configuration argument if none provided and inventory.xml exists
DEFAULT_CONFIG_ARGS=()
if [ $# -eq 0 ]; then
    if [ -f "$APP_DIR/inventory.xml" ]; then
        DEFAULT_CONFIG_ARGS=("-c" "$APP_DIR/inventory.xml")
    elif [ -f "$PROJECT_ROOT/inventory.xml" ]; then
        DEFAULT_CONFIG_ARGS=("-c" "$PROJECT_ROOT/inventory.xml")
    fi
fi

echo "[Raspi-Inventory] Starting headless scanner daemon..."
exec "$JAVA_CMD" "${JVM_OPTS[@]}" -cp "$CLASSPATH" org.objectspace.rfid.raspi.RaspiInventory "${DEFAULT_CONFIG_ARGS[@]}" "$@"
