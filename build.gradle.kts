plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

tasks {
    wrapper {
        gradleVersion = "8.1.1"
        distributionSha256Sum = "e111cb9948407e26351227dabce49822fb88c37ee72f1d1582a69c68af2e702f"
    }
}
