plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

tasks {
    wrapper {
        gradleVersion = "8.4"
        distributionSha256Sum = "3e1af3ae886920c3ac87f7a91f816c0c7c436f276a6eefdb3da152100fef72ae"
    }
}
