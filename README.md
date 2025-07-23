# Android GUI for [WireGuard](https://www.wireguard.com/)

## Scripts

### Init

```./switch-env.sh staging```

### Build

```./build.sh staging```

### Switch environments

```./switch-env.sh staging```


## Dev

Create a keystore/ folder with 2 files

- the keystore jks file
- a file keystore.properties with the following content

```
storePassword=XXX
keyAlias=isolation-signing-key
keyPassword=XXX
storeFile=../keystore/signing-key.jks
```

**[Download from the Play Store](https://play.google.com/store/apps/details?id=com.jimberisolation.android)**

This is an Android GUI for [WireGuard](https://www.wireguard.com/). It [opportunistically uses the kernel implementation](https://git.zx2c4.com/android_kernel_wireguard/about/), and falls back to using the non-root [userspace implementation](https://git.zx2c4.com/wireguard-go/about/).

## Building

```
$ git clone --recurse-submodules https://git.zx2c4.com.jimberisolation.android
$ cd wireguard-android
$ ./gradlew assembleRelease
```

macOS users may need [flock(1)](https://github.com/discoteq/flock).

## Embedding

The tunnel library is [on Maven Central](https://search.maven.org/artifact/com.jimberisolation.android/tunnel), alongside [extensive class library documentation](https://javadoc.io/doc/com.jimberisolation.android/tunnel).

```
implementation 'com.jimberisolation.android:tunnel:$wireguardTunnelVersion'
```

The library makes use of Java 8 features, so be sure to support those in your gradle configuration with [desugaring](https://developer.android.com/studio/write/java8-support#library-desugaring):

```
compileOptions {
    sourceCompatibility JavaVersion.VERSION_17
    targetCompatibility JavaVersion.VERSION_17
    coreLibraryDesugaringEnabled = true
}
dependencies {
    coreLibraryDesugaring "com.android.tools:desugar_jdk_libs:2.0.3"
}
```

## Translating

Please help us translate the app into several languages on [our translation platform](https://crowdin.com/project/WireGuard).


