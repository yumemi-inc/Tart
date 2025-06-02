import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.tart.publish)
}

group = "io.yumemi.tart"
version = libs.versions.tart.get()

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    jvm()
    js(IR) {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // put your multiplatform dependencies here
                implementation(project(":tart-core"))
                implementation(libs.logger.kermit)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
            }
        }

        val mobileAndDesktop by creating {
            dependsOn(commonMain)
        }
        listOf(
            androidMain,
            iosX64Main,
            iosArm64Main,
            iosSimulatorArm64Main,
            jvmMain,
        ).forEach {
            it.get().dependsOn(mobileAndDesktop)
        }

        val web by creating {
            dependsOn(commonMain)
        }
        listOf(
            jsMain,
        ).forEach {
            it.get().dependsOn(web)
        }
    }
}

android {
    namespace = "io.yumemi.tart.logging"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishConvention {
    artifactId = "tart-logging"
}
