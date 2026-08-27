import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Configures coverage for a KMP module or aggregates all covered modules at the root. */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(KOVER_PLUGIN_ID)

            extensions.configure<KoverProjectExtension> {
                reports {
                    filters {
                        excludes {
                            androidGeneratedClasses()
                            classes("*.generated.resources.*")
                        }
                    }
                }
            }

            if (this == rootProject) {
                subprojects.forEach { coveredProject ->
                    coveredProject.pluginManager.withPlugin(KOVER_PLUGIN_ID) {
                        dependencies.add("kover", coveredProject)
                    }
                }
            }
        }
    }

    private companion object {
        const val KOVER_PLUGIN_ID = "org.jetbrains.kotlinx.kover"
    }
}
