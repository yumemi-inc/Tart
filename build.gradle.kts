plugins {
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.vanniktech.mavenPublish) apply false

    alias(libs.plugins.tart.publish) apply false
}
