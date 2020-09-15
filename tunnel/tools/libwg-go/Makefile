# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.

BUILDDIR ?= $(CURDIR)/build
DESTDIR ?= $(CURDIR)/out

NDK_GO_ARCH_MAP_x86 := 386
NDK_GO_ARCH_MAP_x86_64 := amd64
NDK_GO_ARCH_MAP_arm := arm
NDK_GO_ARCH_MAP_arm64 := arm64
NDK_GO_ARCH_MAP_mips := mipsx
NDK_GO_ARCH_MAP_mips64 := mips64x

CLANG_FLAGS := --target=$(ANDROID_LLVM_TRIPLE) --gcc-toolchain=$(ANDROID_TOOLCHAIN_ROOT) --sysroot=$(ANDROID_SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(CFLAGS)
export CGO_LDFLAGS := $(CLANG_FLAGS) $(LDFLAGS) -Wl,-soname=libwg-go.so
export CC := $(ANDROID_C_COMPILER)
export GOARCH := $(NDK_GO_ARCH_MAP_$(ANDROID_ARCH_NAME))
export GOOS := android
export CGO_ENABLED := 1

GO_VERSION := 1.15.2
GO_PLATFORM := $(shell uname -s | tr '[:upper:]' '[:lower:]')-$(NDK_GO_ARCH_MAP_$(shell uname -m))
GO_TARBALL := go$(GO_VERSION).$(GO_PLATFORM).tar.gz
GO_HASH_darwin-amd64 := 9bd39600d9fa1fa4a5ccce8761d249f7421cffe671376f791293c4138f3d7c62
GO_HASH_linux-amd64 := b49fda1ca29a1946d6bb2a5a6982cf07ccd2aba849289508ee0f9918f6bb4552

default: $(DESTDIR)/libwg-go.so

$(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL):
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	curl -o "$@.tmp" "https://dl.google.com/go/$(GO_TARBALL)" && \
	echo "$(GO_HASH_$(GO_PLATFORM))  $@.tmp" | sha256sum -c && \
	mv "$@.tmp" "$@"'

$(BUILDDIR)/go-$(GO_VERSION)/.prepared: $(GRADLE_USER_HOME)/caches/golang/$(GO_TARBALL)
	mkdir -p "$(dir $@)"
	flock "$@.lock" -c ' \
	[ -f "$@" ] && exit 0; \
	tar -C "$(dir $@)" --strip-components=1 -xzf "$^" && \
	patch -p1 -f -N -r- -d "$(dir $@)" < goruntime-boottime-over-monotonic.diff && \
	touch "$@"'

$(DESTDIR)/libwg-go.so: export PATH := $(BUILDDIR)/go-$(GO_VERSION)/bin/:$(PATH)
$(DESTDIR)/libwg-go.so: $(BUILDDIR)/go-$(GO_VERSION)/.prepared go.mod
	go build -tags linux -ldflags="-X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/$(ANDROID_PACKAGE_NAME)/cache/wireguard" -v -trimpath -o "$@" -buildmode c-shared

.DELETE_ON_ERROR:
