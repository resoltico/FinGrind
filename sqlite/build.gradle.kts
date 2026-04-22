plugins {
    `java-library`
    id("dev.erst.fingrind.java-conventions")
}

description = "SQLite-backed FinGrind persistence adapter"

dependencies { implementation(project(":contract")); implementation(project(":executor")) }
