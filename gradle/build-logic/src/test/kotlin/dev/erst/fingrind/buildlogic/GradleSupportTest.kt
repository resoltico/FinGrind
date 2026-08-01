package dev.erst.fingrind.buildlogic

import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder

class GradleSupportTest {
    @Test
    fun cliAndCoreNativeAccess_isGrantedByOneExactArgument() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register<JavaExec>("nativeAccess").get()

        task.enableCliAndCoreNamedNativeAccess()

        assertTrue(
            task.jvmArgs.contains("--enable-native-access=$CLI_AND_CORE_NATIVE_ACCESS_MODULES"),
        )
    }
}
