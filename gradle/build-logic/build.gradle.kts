import java.io.File
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("com.diffplug.spotless:spotless-plugin-gradle:${libs.versions.spotless.get()}")
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:${libs.versions.errorprone.plugin.get()}")
}

gradlePlugin {
    plugins {
        register("fingrindJavaConventions") {
            id = "fingrind.java-conventions"
            implementationClass = "dev.erst.fingrind.buildlogic.FinGrindJavaConventionsPlugin"
        }
        register("fingrindRootConventions") {
            id = "fingrind.root-conventions"
            implementationClass = "dev.erst.fingrind.buildlogic.FinGrindRootConventionsPlugin"
        }
        register("fingrindJazzerConventions") {
            id = "fingrind.jazzer-conventions"
            implementationClass = "dev.erst.fingrind.buildlogic.FinGrindJazzerConventionsPlugin"
        }
    }
}

// FinGrind's runtime and product-module baseline is Java 26. Kotlin 2.3.0 does not yet emit
// JVM 26 bytecode directly, so shared build logic compiles with the Java 26 toolchain while
// still targeting JVM 25 bytecode.
kotlin {
    jvmToolchain(26)
}

tasks.withType<KotlinCompile>().configureEach {
    incremental = false
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    doFirst {
        cleanDirectoryContents(destinationDirectory.get().asFile)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    doFirst {
        cleanDirectoryContents(destinationDirectory.get().asFile)
    }
}

fun cleanDirectoryContents(directory: File) {
    if (!directory.exists()) {
        directory.mkdirs()
        return
    }
    directory.listFiles()?.forEach(File::deleteRecursively)
}
