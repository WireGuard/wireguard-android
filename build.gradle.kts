plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

tasks {
    wrapper {
        gradleVersion = "8.10.1"
        distributionSha256Sum = "1541fa36599e12857140465f3c91a97409b4512501c26f9631fb113e392c5bd1"
    }
}
