containing = $(foreach v,$2,$(if $(findstring $1,$v),$v))
FILES := $(wildcard ../wireguard-go/*/*.go) $(wildcard ../wireguard-go/*.go)
FILES := $(filter-out %/main.go $(filter-out %_linux.go,$(call containing,_,$(FILES))),$(FILES))

export GOPATH := $(CURDIR)/gopath
export GOROOT := $(CURDIR)/goroot
export PATH := $(GOROOT)/bin:$(PATH)
CLANG_FLAGS := --target=$(ANDROID_LLVM_TRIPLE) --gcc-toolchain=$(ANDROID_TOOLCHAIN_ROOT) --sysroot=$(ANDROID_SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(CFLAGS)
export CGO_LDFLAGS := $(CLANG_FLAGS) $(LDFLAGS)
export CC := $(ANDROID_C_COMPILER)
GO_ARCH_FILTER := case "$(ANDROID_ARCH_NAME)" in x86) echo 386 ;; x86_64) echo amd64 ;; *) echo $(ANDROID_ARCH_NAME) ;; esac
export GOARCH := $(shell $(GO_ARCH_FILTER))
export GOOS := android
export CGO_ENABLED := 1

GORELEASETARBALL := https://dl.google.com/go/go1.10.1.$(shell uname -s | tr '[:upper:]' '[:lower:]')-amd64.tar.gz

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
