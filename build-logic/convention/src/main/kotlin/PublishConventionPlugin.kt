import io.github.komakt.koma.buildlogic.PublishConventionExtension
import io.github.komakt.koma.buildlogic.dsl.alias
import io.github.komakt.koma.buildlogic.dsl.libs
import io.github.komakt.koma.buildlogic.dsl.mavenPublishing
import io.github.komakt.koma.buildlogic.dsl.plugin
import io.github.komakt.koma.buildlogic.publish.pom
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

@Suppress("unused")
class PublishConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.alias(libs.plugin("vanniktech-mavenPublish"))

            mavenPublishing {
                publishToMavenCentral()

                if (System.getenv("ORG_GRADLE_PROJECT_mavenCentralUsername") != null) {
                    signAllPublications()
                }

                pom()
            }

            val publishConvention = extensions.create("publishConvention", PublishConventionExtension::class)

            afterEvaluate {
                publishConvention.applyToProject(target)
            }
        }
    }
}
