#!/bin/bash

set -e

ARCH="arm64"
ANDROID_PLATFORM="24"
WIREGUARD_VERSION="0.0.20171111"
LIBMNL_VERSION="1.0.4"

case "$ARCH" in
	arm) ANDROID_MACHINE="arm-linux-androideabi"; ;;
	arm64) ANDROID_MACHINE="aarch64-linux-android"; ;;
	mips) ANDROID_MACHINE="mipsel-linux-android"; ;;
	mips64) ANDROID_MACHINE="mips64el-linux-android"; ;;
	x86) ANDROID_MACHINE="x86-linux-android"; ;;
	x86_64) ANDROID_MACHINE="x86_64-linux-android"; ;;
	*) echo "Error: unknown architecture" >&2; exit 1; ;;
esac

GCC_VERSION="4.9"
ANDROID_PLATFORM="/opt/android-ndk/platforms/android-$ANDROID_PLATFORM/arch-$ARCH/usr"
ANDROID_TOOLCHAIN="/opt/android-ndk/toolchains/$ANDROID_MACHINE-$GCC_VERSION/prebuilt/linux-$(uname -m)/bin"

export PATH="$ANDROID_TOOLCHAIN:$PATH"
export CC="$ANDROID_MACHINE-gcc --sysroot $ANDROID_PLATFORM"
export LD="$ANDROID_MACHINE-ld --sysroot $ANDROID_PLATFORM"
export CFLAGS="-O3 -fomit-frame-pointer -I$ANDROID_PLATFORM/include -fPIE"
export LDFLAGS="-pie"

trap 'cd /; rm -rf "$where"' EXIT
where="$(mktemp -d)"
here="$PWD"
cd "$where"

wget "http://ftp.netfilter.org/pub/libmnl/libmnl-$LIBMNL_VERSION.tar.bz2"
wget "https://git.zx2c4.com/WireGuard/snapshot/WireGuard-$WIREGUARD_VERSION.tar.xz"

tar xjf "libmnl-$LIBMNL_VERSION.tar.bz2"
tar xJf "WireGuard-$WIREGUARD_VERSION.tar.xz"

cd "libmnl-$LIBMNL_VERSION"
./configure --enable-static --disable-shared --host="$ANDROID_MACHINE"
make -j$(nproc)

cd ..

cd "WireGuard-$WIREGUARD_VERSION/src/tools"
export CFLAGS="$CFLAGS -I../../../libmnl-$LIBMNL_VERSION/include"
export LDFLAGS="$LDFLAGS -L../../../libmnl-$LIBMNL_VERSION/src/.libs"
make -j$(nproc)
"$ANDROID_MACHINE-strip" wg
mv wg "$here/wg"

echo
echo
echo ===============================================
echo Build complete:
ls -l "$here/wg"
echo ===============================================
