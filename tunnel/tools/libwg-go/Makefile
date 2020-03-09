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
export CGO_LDFLAGS := $(CLANG_FLAGS) $(LDFLAGS)
export CC := $(ANDROID_C_COMPILER)
export GOARCH := $(NDK_GO_ARCH_MAP_$(ANDROID_ARCH_NAME))
export GOOS := android
export CGO_ENABLED := 1

DESIRED_GO_VERSION := 1.13.7

default: $(DESTDIR)/libwg-go.so

$(BUILDDIR)/go-$(DESIRED_GO_VERSION)/.prepared:
	mkdir -p "$(dir $@)"
	curl "https://dl.google.com/go/go$(DESIRED_GO_VERSION).$(shell uname -s | tr '[:upper:]' '[:lower:]')-$(NDK_GO_ARCH_MAP_$(shell uname -m)).tar.gz" | tar -C "$(dir $@)" --strip-components=1 -xzf -
	patch -p1 -f -N -r- -d "$(dir $@)" < goruntime-boottime-over-monotonic.diff
	touch "$@"

$(DESTDIR)/libwg-go.so: export PATH := $(BUILDDIR)/go-$(DESIRED_GO_VERSION)/bin/:$(PATH)
$(DESTDIR)/libwg-go.so: $(BUILDDIR)/go-$(DESIRED_GO_VERSION)/.prepared go.mod
	go build -tags linux -ldflags="-X golang.zx2c4.com/wireguard/ipc.socketDirectory=/data/data/$(ANDROID_PACKAGE_NAME)/cache/wireguard" -v -trimpath -o "$@" -buildmode c-shared
