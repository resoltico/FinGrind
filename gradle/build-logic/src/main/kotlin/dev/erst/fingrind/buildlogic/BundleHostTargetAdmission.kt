package dev.erst.fingrind.buildlogic

import org.gradle.api.GradleException

/** Enforces the host-native boundary for self-contained bundle production. */
internal object BundleHostTargetAdmission {
    fun requireHostNative(
        requestedTarget: BundleTargetContract,
        hostTarget: BundleTargetContract,
    ) {
        if (requestedTarget.classifier == hostTarget.classifier) {
            return
        }
        throw GradleException(
            "FinGrind bundle builds are host-native only. Requested classifier " +
                "${requestedTarget.classifier} but the current host can only build " +
                "${hostTarget.classifier} because the private runtime image and managed SQLite library " +
                "are produced for the active host platform.",
        )
    }
}
