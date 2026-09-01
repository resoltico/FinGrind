package dev.erst.fingrind.buildlogic

import javax.inject.Inject
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/** Project-owned release-critical mutation scope and its reviewed per-class evidence baseline. */
abstract class FinGrindMutationScopeExtension @Inject constructor(objects: ObjectFactory) {
    val targetClasses: SetProperty<String> = objects.setProperty(String::class.java)
    val targetTests: SetProperty<String> = objects.setProperty(String::class.java)
    val expectedMutationCounts: MapProperty<String, Int> =
        objects.mapProperty(String::class.java, Int::class.java)
    val excludedProductionClasses: MapProperty<String, String> =
        objects.mapProperty(String::class.java, String::class.java)
}
