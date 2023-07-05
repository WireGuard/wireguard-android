@file:Suppress("UnstableApiUsage")

import org.gradle.api.tasks.testing.logging.TestLogEvent

val pkg: String = providers.gradleProperty("wireguardPackageName").get()

plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
    signing
}

android {
    compileSdk = 34
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    namespace = "${pkg}.tunnel"
    defaultConfig {
        minSdk = 21
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
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.collection)
    compileOnly(libs.jsr305)
    testImplementation(libs.junit)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = pkg
            artifactId = "tunnel"
            version = providers.gradleProperty("wireguardVersionName").get()
            afterEvaluate {
                from(components["release"])
            }
            pom {
                name.set("WireGuard Tunnel Library")
                description.set("Embeddable tunnel library for WireGuard for Android")
                url.set("https://www.wireguard.com/")

                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    connection.set("scm:git:https://git.zx2c4.com/wireguard-android")
                    developerConnection.set("scm:git:https://git.zx2c4.com/wireguard-android")
                    url.set("https://git.zx2c4.com/wireguard-android")
                }
                developers {
                    organization {
                        name.set("WireGuard")
                        url.set("https://www.wireguard.com/")
                    }
                    developer {
                        name.set("WireGuard")
                        email.set("team@wireguard.com")
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
