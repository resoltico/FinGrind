plugins {
    `java-library`
    id("dev.erst.fingrind.java-conventions")
}

description = "PDF report rendering adapter for FinGrind reporting results"

dependencies { api(project(":contract")); implementation(libs.pdfbox) }
