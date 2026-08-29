#!/bin/sh
set -eu

HEV_VERSION="2.16.0"
HEV_COMMIT="0a05221275a51a884d93328c55fc2fbc9e9b6974"
ARCH="${CURRENT_ARCH:-}"
if [ -z "$ARCH" ] || [ "$ARCH" = "undefined_arch" ]; then
    ARCH=$(printf '%s' "$ARCHS" | awk '{print $1}')
fi
OUTPUT_DIR="$SRCROOT/build/native/$SDK_NAME"
OUTPUT="$OUTPUT_DIR/libhev-socks5-tunnel.a"
CACHE_ROOT="${DERIVED_FILE_DIR}/knet-hev-${HEV_VERSION}-${SDK_NAME}-${ARCH}"
SOURCE="$CACHE_ROOT/source"

if [ -f "$OUTPUT" ]; then
    exit 0
fi

mkdir -p "$CACHE_ROOT" "$OUTPUT_DIR"
if [ ! -d "$SOURCE/.git" ]; then
    git clone --quiet --recursive --branch "$HEV_VERSION" --depth 1 \
        https://github.com/heiher/hev-socks5-tunnel.git "$SOURCE"
fi

ACTUAL_COMMIT=$(git -C "$SOURCE" rev-parse HEAD)
if [ "$ACTUAL_COMMIT" != "$HEV_COMMIT" ]; then
    echo "error: unexpected hev-socks5-tunnel source revision $ACTUAL_COMMIT" >&2
    exit 1
fi

case "$SDK_NAME" in
    iphoneos*) MIN_VERSION="-mios-version-min=16.0" ;;
    iphonesimulator*) MIN_VERSION="-mios-simulator-version-min=16.0" ;;
    *) echo "error: unsupported KNet packet-tunnel SDK $SDK_NAME" >&2; exit 1 ;;
esac

(
    cd "$SOURCE"
    make clean >/dev/null 2>&1 || true
    make PP="xcrun --sdk $SDK_NAME --toolchain $SDK_NAME clang" \
         CC="xcrun --sdk $SDK_NAME --toolchain $SDK_NAME clang" \
         CFLAGS="-arch $ARCH $MIN_VERSION" \
         LFLAGS="-arch $ARCH $MIN_VERSION -Wl,-Bsymbolic-functions" static
    libtool -static -o "$OUTPUT" \
        bin/libhev-socks5-tunnel.a \
        third-part/lwip/bin/liblwip.a \
        third-part/yaml/bin/libyaml.a \
        third-part/hev-task-system/bin/libhev-task-system.a
)
