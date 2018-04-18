containing = $(foreach v,$2,$(if $(findstring $1,$v),$v))
FILES := $(wildcard ../wireguard-go/*/*.go) $(wildcard ../wireguard-go/*.go)
FILES := $(filter-out %/main.go $(filter-out %_linux.go,$(call containing,_,$(FILES))),$(FILES)) $(wildcard ../wireguard-go/syscall_linux*.go)

export GOPATH := $(CURDIR)/go
CLANG_FLAGS := --target=$(ANDROID_LLVM_TRIPLE) --gcc-toolchain=$(ANDROID_TOOLCHAIN_ROOT) --sysroot=$(ANDROID_SYSROOT)
export CGO_CFLAGS := $(CLANG_FLAGS) $(CFLAGS)
export CGO_LDFLAGS := $(CLANG_FLAGS) $(LDFLAGS)
export CC := $(ANDROID_C_COMPILER)
GO_ARCH_FILTER := case "$(ANDROID_ARCH_NAME)" in x86) echo 386 ;; x86_64) echo amd64 ;; *) echo $(ANDROID_ARCH_NAME) ;; esac
export GOARCH := $(shell $(GO_ARCH_FILTER))
export GOOS := android
export CGO_ENABLED := 1

$(DESTDIR)/libwg-go.so: $(FILES) api-android.go jni.c
	find . -name '*.go' -type l -delete
	find . -type d -empty -delete
	mkdir -p $(subst ../wireguard-go/,./,$(dir $(FILES)))
	$(foreach FILE,$(FILES),ln -sfrt $(subst ../wireguard-go/,./,$(dir $(FILE))) $(FILE);)
	go get -v -d
	go build -v -o $(DESTDIR)/libwg-go.so -buildmode c-shared
