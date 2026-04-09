import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

// FinGrind's runtime and product-module baseline is Java 26. Kotlin 2.3.0 does not yet emit
// JVM 26 bytecode directly, so build logic in buildSrc compiles with the Java 26 toolchain while
// still targeting JVM 25 bytecode. That keeps the shell- and launcher-level Java contract at 26
// without requiring a separate local Java 25 installation.
kotlin {
    jvmToolchain(26)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}
