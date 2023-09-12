plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

tasks {
    wrapper {
        gradleVersion = "8.3"
        distributionSha256Sum = "591855b517fc635b9e04de1d05d5e76ada3f89f5fc76f87978d1b245b4f69225"
    }
}
