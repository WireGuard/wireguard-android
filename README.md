# Android GUI for [HEZWIN](https://www.hezwin.com/)

**[Download from the Play Store](https://play.google.com/store/apps/details?id=com.hezwin.android)**

This is an Android GUI for [HEZWIN](https://www.hezwin.com/). It [opportunistically uses the kernel implementation](https://git.zx2c4.com/android_kernel_hezwin/about/), and falls back to using the non-root [userspace implementation](https://git.zx2c4.com/hezwin-go/about/).

## Building

```
$ git clone --recurse-submodules https://git.zx2c4.com/hezwin-android
$ cd hezwin-android
$ ./gradlew assembleRelease
```

macOS users may need [flock(1)](https://github.com/discoteq/flock).

## Embedding

The tunnel library is [on Maven Central](https://search.maven.org/artifact/com.hezwin.android/tunnel), alongside [extensive class library documentation](https://javadoc.io/doc/com.hezwin.android/tunnel).

```
implementation 'com.hezwin.android:tunnel:$hezwinTunnelVersion'
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

Please help us translate the app into several languages on [our translation platform](https://crowdin.com/project/HEZWIN).
