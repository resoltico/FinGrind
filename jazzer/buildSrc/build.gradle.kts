import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
}

// The nested Jazzer build participates in the same local Java 26 shell contract as the root
// project. Its build logic is plain Java, so compiling with the Java 26 toolchain and emitting
// Java 25 bytecode preserves compatibility without requiring a separate local Java 25 install.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}
