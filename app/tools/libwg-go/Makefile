# SPDX-License-Identifier: GPL-2.0
#
# Copyright © 2018 Jason A. Donenfeld <Jason@zx2c4.com>. All Rights Reserved.

containing = $(foreach v,$2,$(if $(findstring $1,$v),$v))
FILES := $(wildcard ../wireguard-go/*/*.go) $(wildcard ../wireguard-go/*.go)
FILES := $(filter-out %/main.go $(filter-out %_linux.go,$(call containing,_,$(FILES))),$(FILES))

NDK_GO_ARCH_MAP_x86 := 386
NDK_GO_ARCH_MAP_x86_64 := amd64
NDK_GO_ARCH_MAP_arm := arm
NDK_GO_ARCH_MAP_arm64 := arm64
NDK_GO_ARCH_MAP_mips := mipsx
NDK_GO_ARCH_MAP_mips64 := mips64x

export GOPATH := $(CURDIR)/gopath
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
GOBUILDVERSION := 1.10.2
GOBUILDTARBALL := https://dl.google.com/go/go$(GOBUILDVERSION).$(GOBUILDOS)-$(GOBUILDARCH).tar.gz
GOBUILDVERSION_NEEDED := go version go$(GOBUILDVERSION) $(GOBUILDOS)/$(GOBUILDARCH)

GOBUILDVERSION_CURRENT := $(shell go version 2>/dev/null)
ifeq ($(GOBUILDVERSION_NEEDED),$(GOBUILDVERSION_CURRENT))
$(info Using system go)
else
export GOROOT := $(CURDIR)/goroot
export PATH := $(GOROOT)/bin:$(PATH)
GOBUILDVERSION_CURRENT := $(shell $(GOROOT)/bin/go version 2>/dev/null)
ifneq ($(GOBUILDVERSION_NEEDED),$(GOBUILDVERSION_CURRENT))
$(shell rm -f $(GOROOT)/bin/go)
endif
$(GOROOT)/bin/go:
	rm -rf "$(GOROOT)"
	mkdir -p "$(GOROOT)"
	curl "$(GOBUILDTARBALL)" | tar -C "$(GOROOT)" --strip-components=1 -xzf - || { rm -rf "$(GOROOT)"; exit 1; }
	patch -p1 -f -N -r- -d"$(GOROOT)" < goruntime-boottime-over-monotonic.diff || { rm -rf "$(GOROOT)"; exit 1; }
$(DESTDIR)/libwg-go.so: $(GOROOT)/bin/go
endif

$(shell test "$$(cat .gobuildversion 2>/dev/null)" = "$(GOBUILDVERSION_CURRENT)" || rm -f "$(DESTDIR)/libwg-go.so")

$(DESTDIR)/libwg-go.so: $(FILES) api-android.go jni.c
	find . -name '*.go' -type l -delete
	find . -type d -empty -delete
	mkdir -p $(subst ../wireguard-go/,./,$(dir $(FILES)))
	$(foreach FILE,$(FILES),ln -sfrt $(subst ../wireguard-go/,./,$(dir $(FILE))) $(FILE);)
	patch -p1 -f -R -s --dry-run -d"$$(go env GOROOT)" < goruntime-boottime-over-monotonic.diff
	go get -v -d
	go build -v -o $(DESTDIR)/libwg-go.so -buildmode c-shared
	go version > .gobuildversion
