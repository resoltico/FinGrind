package dev.erst.fingrind.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

/** Installs the reviewed production mutation scope for one deterministic accounting module. */
class FinGrindProductionMutationScopePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(FinGrindMutationConventionsPlugin::class.java)
        val scope = project.extensions.getByType<FinGrindMutationScopeExtension>()
        MutationScopeCatalog.configure(project, scope)
    }
}
