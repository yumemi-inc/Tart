package io.yumemi.tart.buildlogic.publish

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.maven.MavenPomDeveloperSpec
import org.gradle.api.publish.maven.MavenPomLicenseSpec

internal fun MavenPublishBaseExtension.pom() {
    pom {
        name.set("Tart")
        description.set("A Kotlin Multiplatform Flux framework.")
        inceptionYear.set("2024")
        url.set("https://github.com/yumemi-inc/Tart/")
        licenses {
            mit()
        }
        developers {
            yumemiInc()
        }
        scm {
            url.set("https://github.com/yumemi-inc/Tart/")
            connection.set("scm:git:git://github.com/yumemi-inc/Tart.git")
            developerConnection.set("scm:git:git://github.com/yumemi-inc/Tart.git")
        }
    }
}

private fun MavenPomLicenseSpec.mit() {
    license {
        name.set("MIT")
        url.set("https://opensource.org/licenses/MIT")
        distribution.set("https://opensource.org/licenses/MIT")
    }
}

private fun MavenPomDeveloperSpec.yumemiInc() {
    developer {
        id.set("yumemi-inc")
        name.set("YUMEMI Inc.")
        url.set("https://github.com/yumemi-inc/")
    }
}
