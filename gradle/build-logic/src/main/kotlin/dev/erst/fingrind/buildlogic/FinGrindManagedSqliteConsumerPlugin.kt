package dev.erst.fingrind.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class FinGrindManagedSqliteConsumerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project != project.rootProject) {
            "dev.erst.fingrind.managed-sqlite-consumer applies only to subprojects."
        }
        project.pluginManager.withPlugin("java") {
            ManagedSqliteProvisioningLogic.configureConsumers(
                project,
                ManagedSqliteProvisioningRegistry.require(project.rootProject),
            )
        }
    }
}
