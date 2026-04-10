import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    gradlePluginPortal()
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
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
    doFirst {
        destinationDirectory.get().asFile.deleteRecursively()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    doFirst {
        destinationDirectory.get().asFile.deleteRecursively()
    }
}
