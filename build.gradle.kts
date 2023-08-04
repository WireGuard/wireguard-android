plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

tasks {
    wrapper {
        gradleVersion = "8.2.1"
        distributionSha256Sum = "03ec176d388f2aa99defcadc3ac6adf8dd2bce5145a129659537c0874dea5ad1"
    }
}
