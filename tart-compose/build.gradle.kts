import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "io.yumemi.tart"
version = "1.0.0-beta01"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }
    // FIXME: comment out iOS because build error
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                // put your multiplatform dependencies here
                implementation(project(":tart-core"))
                implementation(libs.compose.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

android {
    namespace = "io.yumemi.tart.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    if (System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername") != null) {
        signAllPublications()
    }

    coordinates(group.toString(), "tart-compose", version.toString())

    pom {
        name = "Tart"
        description = "A Kotlin Multiplatform Flux framework."
        inceptionYear = "2024"
        url = "https://github.com/yumemi-inc/Tart/"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
                distribution = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "yumemi-inc"
                name = "YUMEMI Inc."
                url = "https://github.com/yumemi-inc/"
            }
        }
        scm {
            url = "https://github.com/yumemi-inc/Tart/"
            connection = "scm:git:git://github.com/yumemi-inc/Tart.git"
            developerConnection = "scm:git:git://github.com/yumemi-inc/Tart.git"
        }
    }
}
