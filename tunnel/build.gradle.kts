@file:Suppress("UnstableApiUsage")

import org.gradle.api.tasks.testing.logging.TestLogEvent

val pkg: String = providers.gradleProperty("wireguardPackageName").getOrElse("com.wireguard.android")

plugins {
    id("com.android.library")
    `maven-publish`
    signing
}

android {
    compileSdk = 36
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    namespace = "${pkg}.tunnel"
    defaultConfig {
        minSdk = 28
    }
    externalNativeBuild {
        cmake {
            path("tools/CMakeLists.txt")
        }
    }
    testOptions.unitTests.all {
        it.testLogging { events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED) }
    }
    buildTypes {
        all {
            externalNativeBuild {
                cmake {
                    targets("libwg-go.so", "libwg.so", "libwg-quick.so")
                    arguments("-DGRADLE_USER_HOME=${project.gradle.gradleUserHomeDir}")
                    arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                }
            }
        }
        release {
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_PACKAGE_NAME=${pkg}")
                }
            }
        }
        debug {
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_PACKAGE_NAME=${pkg}.debug")
                }
            }
        }
    }
    lint {
        disable += "LongLogTag"
        disable += "NewApi"
    }
    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.annotation)
    implementation(libs.androidx.core)
    compileOnly(libs.javax.annotation.api)
    compileOnly(libs.jsr305)
    testImplementation(libs.junit4)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = pkg
            artifactId = "tunnel"
            version = providers.gradleProperty("wireguardVersionName").getOrElse("1.0.0")
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name = "WireGuard Tunnel Library"
                description = "Embeddable tunnel library for WireGuard for Android"
                url = "https://www.wireguard.com/"

                licenses {
                    license {
                        name = "The Apache Software License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                        distribution = "repo"
                    }
                }
                scm {
                    connection = "scm:git:https://git.zx2c4.com/wireguard-android"
                    developerConnection = "scm:git:https://git.zx2c4.com/wireguard-android"
                    url = "https://git.zx2c4.com/wireguard-android"
                }
                developers {
                    organization {
                        name = "WireGuard"
                        url = "https://www.wireguard.com/"
                    }
                    developer {
                        name = "WireGuard"
                        email = "team@wireguard.com"
                    }
                }
            }
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
