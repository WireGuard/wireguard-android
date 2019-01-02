# SPDX-License-Identifier: Apache-2.0
#
# Copyright © 2017-2019 WireGuard LLC. All Rights Reserved.

UPSTREAM_FILES := $(filter-out %/main.go %/queueconstants.go,$(wildcard ../wireguard-go/*/*.go) $(wildcard ../wireguard-go/*.go)) ../wireguard-go/go.mod ../wireguard-go/go.sum
DOWNSTREAM_FILES := $(wildcard src/*.go) $(wildcard src/*.c) $(wildcard src/*/*.go)

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

default: $(DESTDIR)/libwg-go.so

GOBUILDARCH := $(NDK_GO_ARCH_MAP_$(shell uname -m))
GOBUILDOS := $(shell uname -s | tr '[:upper:]' '[:lower:]')
GOBUILDVERSION := 1.11.4
GOBUILDTARBALL := https://dl.google.com/go/go$(GOBUILDVERSION).$(GOBUILDOS)-$(GOBUILDARCH).tar.gz
GOBUILDVERSION_NEEDED := go version go$(GOBUILDVERSION) $(GOBUILDOS)/$(GOBUILDARCH)
export GOROOT := $(BUILDDIR)/goroot
export GOPATH := $(BUILDDIR)/gopath
export PATH := $(GOROOT)/bin:$(PATH)
GOBUILDVERSION_CURRENT := $(shell $(GOROOT)/bin/go version 2>/dev/null)
ifneq ($(GOBUILDVERSION_NEEDED),$(GOBUILDVERSION_CURRENT))
$(shell rm -f $(GOROOT)/bin/go)
endif
$(GOROOT)/bin/go:
	rm -rf "$(GOROOT)"
	mkdir -p "$(GOROOT)"
	curl "$(GOBUILDTARBALL)" | tar -C "$(GOROOT)" --strip-components=1 -xzf - || { rm -rf "$(GOROOT)"; exit 1; }
	cat goruntime-*.diff | patch -p1 -f -N -r- -d "$(GOROOT)" || { rm -rf "$(GOROOT)"; exit 1; }

$(shell test "$$(cat .gobuildversion 2>/dev/null)" = "$(GOBUILDVERSION_CURRENT)" || rm -f "$(DESTDIR)/libwg-go.so")

define copy-src-to-build
$(subst $(1),$(BUILDDIR)/,$(2)): $(2)
	@mkdir -vp "$$(dir $$@)"
	@cp -vp "$$<" "$$@"
$(BUILDDIR)/.prepared: $(subst $(1),$(BUILDDIR)/,$(2))
endef

$(foreach FILE,$(UPSTREAM_FILES),$(eval $(call copy-src-to-build,../wireguard-go/,$(FILE))))
$(foreach FILE,$(DOWNSTREAM_FILES),$(eval $(call copy-src-to-build,src/,$(FILE))))

$(BUILDDIR)/.prepared: $(GOROOT)/bin/go
	cd "$(BUILDDIR)" && go get || { chmod -fR +w "$(GOPATH)/pkg/mod"; rm -rf "$(GOPATH)/pkg/mod"; exit 1; }
	chmod -fR +w "$(GOPATH)/pkg/mod"
	touch "$@"

$(DESTDIR)/libwg-go.so: $(BUILDDIR)/.prepared
	cd "$(BUILDDIR)" && go build -ldflags="-X main.socketDirectory=/data/data/$(ANDROID_PACKAGE_NAME)/cache/wireguard" -v -o "$@" -buildmode c-shared
	go version > .gobuildversion
