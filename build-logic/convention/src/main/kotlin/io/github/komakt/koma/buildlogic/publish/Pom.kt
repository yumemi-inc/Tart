package io.github.komakt.koma.buildlogic.publish

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.maven.MavenPomDeveloperSpec
import org.gradle.api.publish.maven.MavenPomLicenseSpec

internal fun MavenPublishBaseExtension.pom() {
    pom {
        name.set("Koma")
        description.set("A Kotlin Multiplatform Flux framework.")
        inceptionYear.set("2024")
        url.set("https://github.com/komakt/koma/")
        developers {
            komakt()
        }
        licenses {
            mit()
        }
        scm {
            url.set("https://github.com/komakt/koma/")
            connection.set("scm:git:git://github.com/komakt/koma.git")
            developerConnection.set("scm:git:git://github.com/komakt/koma.git")
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

private fun MavenPomDeveloperSpec.komakt() {
    developer {
        id.set("komakt")
        name.set("komakt")
        url.set("https://github.com/komakt/")
    }
}
