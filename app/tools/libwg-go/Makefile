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
export GOROOT := $(CURDIR)/goroot
export PATH := $(GOROOT)/bin:$(PATH)
CLANG_FLAGS := --target=$(ANDROID_LLVM_TRIPLE) --gcc-toolchain=$(ANDROID_TOOLCHAIN_ROOT) --sysroot=$(ANDROID_SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(CFLAGS)
export CGO_LDFLAGS := $(CLANG_FLAGS) $(LDFLAGS)
export CC := $(ANDROID_C_COMPILER)
export GOARCH := $(NDK_GO_ARCH_MAP_$(ANDROID_ARCH_NAME))
export GOOS := android
export CGO_ENABLED := 1

GORELEASEVERSION := 1.10.1
GORELEASETARBALL := https://dl.google.com/go/go$(GORELEASEVERSION).$(shell uname -s | tr '[:upper:]' '[:lower:]')-$(NDK_GO_ARCH_MAP_$(shell uname -m)).tar.gz

default: $(DESTDIR)/libwg-go.so

$(GOROOT)/bin/go:
	rm -rf "$(GOROOT)"
	mkdir -p "$(GOROOT)"
	curl "$(GORELEASETARBALL)" | tar -C "$(GOROOT)" --strip-components=1 -xzf - || rm -rf "$(GOROOT)"

$(DESTDIR)/libwg-go.so: $(FILES) api-android.go jni.c $(GOROOT)/bin/go
	find . -name '*.go' -type l -delete
	find . -type d -empty -delete
	mkdir -p $(subst ../wireguard-go/,./,$(dir $(FILES)))
	$(foreach FILE,$(FILES),ln -sfrt $(subst ../wireguard-go/,./,$(dir $(FILE))) $(FILE);)
	go get -v -d
	go build -v -o $(DESTDIR)/libwg-go.so -buildmode c-shared
