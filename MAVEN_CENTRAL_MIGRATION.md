# Maven Central Publishing Migration Guide

This document outlines the migration from OSSRH to Maven Central Portal for publishing the WireGuard Android library.

## Background

OSSRH (OSS Repository Hosting) reached end-of-life on June 30, 2025. Maven Central has transitioned to a new Central Portal with redesigned APIs. The legacy Gradle publishing configuration in this project has been updated to use the new system.

## Changes Made

### 1. Plugin Update
- **Old**: Manual `maven-publish` and `signing` plugins
- **New**: `com.vanniktech.maven.publish` plugin v0.34.0

### 2. Configuration Migration
The publishing configuration has been completely replaced:

**Before** (tunnel/build.gradle.kts):
```kotlin
plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    signing
}

publishing {
    publications {
        register<MavenPublication>("release") {
            // Complex manual configuration
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = providers.environmentVariable("SONATYPE_USER").orNull
                password = providers.environmentVariable("SONATYPE_PASSWORD").orNull
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}
```

**After**:
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates(pkg, "tunnel", providers.gradleProperty("wireguardVersionName").get())

    pom {
        // Same POM configuration as before
    }
}
```

## Migration Steps for Maintainers

### 1. Central Portal Account Setup
1. Visit [central.sonatype.com](https://central.sonatype.com)
2. Create account with same email used for OSSRH
3. Click "Migrate Namespace" for existing namespaces
4. Generate User Token in account settings

### 2. Update Environment Variables
Replace OSSRH credentials with Central Portal tokens:

**Old variables**:
- `SONATYPE_USER`
- `SONATYPE_PASSWORD`

**New variables**:
- `ORG_GRADLE_PROJECT_mavenCentralUsername` (from Central Portal User Token)
- `ORG_GRADLE_PROJECT_mavenCentralPassword` (from Central Portal User Token)

### 3. GPG Configuration
GPG signing setup remains the same:
- `ORG_GRADLE_PROJECT_signingInMemoryKey` (base64 encoded private key)
- `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword` (key passphrase)

## Publishing Commands

### Manual Publishing (requires web portal approval)
```bash
./gradlew :tunnel:publishToMavenCentral
```
Then approve release in Central Portal web interface.

### Automatic Publishing (recommended)
```bash
./gradlew :tunnel:publishAndReleaseToMavenCentral
```
Publishes and automatically releases without manual approval.

### Snapshot Publishing
```bash
./gradlew :tunnel:publishToMavenCentral
```
Snapshots are automatically available without approval.

## Validation

After publishing, artifacts should be available at:
- Releases: `https://repo1.maven.org/maven2/com/wireguard/android/tunnel/`
- Snapshots: `https://s01.oss.sonatype.org/content/repositories/snapshots/com/wireguard/android/tunnel/`

## Benefits of New System

1. **Simplified Configuration**: Less boilerplate code
2. **Improved Developer Experience**: Better error messages and documentation
3. **Modern APIs**: More reliable and faster publishing
4. **Automatic Release**: Optional auto-release eliminates manual approval step
5. **Better Security**: Enhanced token-based authentication

## Troubleshooting

### Common Issues
1. **Authentication Failures**: Verify Central Portal credentials are correctly set
2. **Signing Errors**: Ensure GPG key is properly configured and accessible
3. **Namespace Issues**: Confirm namespace migration completed in Central Portal

### Getting Help
- Central Portal Support: central-support@sonatype.com
- Plugin Documentation: https://vanniktech.github.io/gradle-maven-publish-plugin/
- Gradle Community Slack: #maven-central-publishing channel

## References
- [Maven Central Portal Documentation](https://central.sonatype.org/publish/publish-portal-gradle/)
- [Vanniktech Plugin Documentation](https://vanniktech.github.io/gradle-maven-publish-plugin/)
- [OSSRH Sunset Notice](https://central.sonatype.org/pages/ossrh-eol/)