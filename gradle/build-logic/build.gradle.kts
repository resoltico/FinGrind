import java.util.Properties
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val buildMetadata =
    Properties().apply {
        file("../fingrind-build.properties").inputStream().use(::load)
    }
val fingrindJavaVersion =
    buildMetadata.getProperty("fingrindJavaVersion")?.toInt()
        ?: error("Missing fingrindJavaVersion in ../fingrind-build.properties.")

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

System.getProperty("fingrind.gradle.build-logic-dir")
    ?.takeIf { it.isNotBlank() }
    ?.let { buildLogicDirectory ->
        layout.buildDirectory.set(file(buildLogicDirectory))
    }

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:${libs.versions.spotless.get()}")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:${libs.versions.errorprone.plugin.get()}")

    testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
    plugins {
        register("fingrindJavaConventions") {
            id = "dev.erst.fingrind.java-conventions"
            implementationClass = "dev.erst.fingrind.buildlogic.FinGrindJavaConventionsPlugin"
        }
        register("fingrindRootConventions") {
            id = "dev.erst.fingrind.root-conventions"
            implementationClass = "dev.erst.fingrind.buildlogic.FinGrindRootConventionsPlugin"
        }
        register("fingrindJazzerConventions") {
            id = "dev.erst.fingrind.jazzer-conventions"
            implementationClass = "dev.erst.fingrind.buildlogic.FinGrindJazzerConventionsPlugin"
        }
    }
}

// FinGrind's runtime and product-module baseline is Java 26. Kotlin 2.3.0 does not yet emit
// JVM 26 bytecode directly, so shared build logic compiles with the Java 26 toolchain while
// still targeting JVM 25 bytecode.
kotlin {
    jvmToolchain(fingrindJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
