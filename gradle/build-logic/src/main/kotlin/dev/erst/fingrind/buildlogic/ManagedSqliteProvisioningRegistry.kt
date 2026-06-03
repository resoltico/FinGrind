package dev.erst.fingrind.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

private const val managedSqliteProvisioningProperty =
    "dev.erst.fingrind.buildlogic.managed-sqlite-provisioning"

internal object ManagedSqliteProvisioningRegistry {
    fun publish(rootProject: Project, provisioning: ManagedSqliteProvisioning) {
        rootProject.extensions.extraProperties.set(managedSqliteProvisioningProperty, provisioning)
    }

    fun require(rootProject: Project): ManagedSqliteProvisioning =
        rootProject.extensions.extraProperties
            .get(managedSqliteProvisioningProperty)
            .let { value ->
                value as? ManagedSqliteProvisioning
                    ?: throw GradleException(
                        "Root managed SQLite provisioning was not published before consumer configuration.",
                    )
            }
}
