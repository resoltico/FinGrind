import java.util.Properties
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.use.PluginDependency
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
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
    // This pin stays prerelease only until the matching stable Kotlin 2.4.x line is available
    // and verified against FinGrind's included-build/plugin surface.
    kotlin("jvm") version "2.4.0-RC2"
    kotlin("plugin.sam.with.receiver") version "2.4.0-RC2"
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
    compileOnly(gradleKotlinDsl())
    testImplementation(gradleKotlinDsl())
    testImplementation(kotlin("reflect"))
    implementation(gradlePluginCoordinate(libs.plugins.spotless.get()))
    implementation(gradlePluginCoordinate(libs.plugins.errorprone.get()))
    implementation(libs.jackson.databind)
    implementation(libs.pmd.core)
    implementation(libs.pmd.java)
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
        register("fingrindCliDistribution") {
            id = "dev.erst.fingrind.cli-distribution"
            implementationClass = "dev.erst.fingrind.buildlogic.FinGrindCliDistributionPlugin"
        }
    }
}

// FinGrind's runtime and build baseline is Java 26, so the shared build logic now compiles and
// emits JVM 26 bytecode as well.
kotlin {
    jvmToolchain(fingrindJavaVersion)
}

samWithReceiver {
    annotation("org.gradle.api.HasImplicitReceiver")
    annotation("org.gradle.kotlin.dsl.support.ImplicitReceiver")
}

tasks.withType<KotlinCompile>().configureEach {
    outputs.cacheIf {
        // The included-build plugin surface is the repository's control plane. Kotlin 2.4 RC2
        // plus Gradle's externalized build directory can restore compile outputs incompletely from
        // cache here, producing descriptor-only plugin jars. Compile build-logic locally until the
        // upstream cache path is proven safe for this surface.
        false
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(fingrindJavaVersion.toString()))
        jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        freeCompilerArgs.addAll(
            "-java-parameters",
            "-Xsam-conversions=class",
            "-Xjsr305=strict",
            "-Xjspecify-annotations=strict",
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(fingrindJavaVersion)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

fun gradlePluginCoordinate(plugin: PluginDependency): String =
    "${plugin.pluginId}:${plugin.pluginId}.gradle.plugin:${plugin.version}"
