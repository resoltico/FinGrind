package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReviewedSurfaceRegistryTest {
    @Test
    fun javaReviewedSurfaces_loadSplitRegistryFragments() {
        val repositoryRoot = Files.createTempDirectory("reviewed-surface-registry")
        writeJavaFragment(
            repositoryRoot = repositoryRoot,
            fileName = "java/example.json",
            body =
                """
                {
                  "projectPath": "cli",
                  "relativePath": "src/main/java/dev/erst/fingrind/cli/json/Example.java",
                  "owner": "example-owner",
                  "reason": "Example reason.",
                  "splitTrigger": "Split the example owner.",
                  "reviewedRoleName": "example-reviewed-surface",
                  "budgetVarianceReason": "Example variance.",
                  "approval": {
                    "physicalLines": 10,
                    "logicalLines": 9,
                    "importLikeLines": 1,
                    "nestedTypes": 1,
                    "functions": 2,
                    "fieldsPerTopLevelType": 1,
                    "switchArmsPerMethod": 0,
                    "methodLineSpan": 6,
                    "methodParameters": 2,
                    "methodDecisionPoints": 1,
                    "expiresOn": "2026-08-16"
                  }
                }
                """.trimIndent(),
        )
        writeJavaFragment(
            repositoryRoot = repositoryRoot,
            fileName = "text/example.json",
            body =
                """
                {
                  "relativePath": "sqlite/src/main/resources/dev/erst/fingrind/sqlite/example.sql",
                  "owner": "sqlite-example",
                  "reason": "Example text reason.",
                  "splitTrigger": "Split the example text owner.",
                  "reviewedRoleName": "sqlite-example-surface",
                  "budgetVarianceReason": "Example text variance.",
                  "approval": {
                    "physicalLines": 20,
                    "logicalLines": 19,
                    "importLikeLines": 1,
                    "functions": 0,
                    "nestedTypes": 0,
                    "expiresOn": "2026-08-16"
                  }
                }
                """.trimIndent(),
        )

        val surfaces = ReviewedSurfaceRegistry.javaReviewedSurfaces(repositoryRoot)

        assertEquals(1, surfaces.size)
        val surface = surfaces.single()
        assertEquals("cli", surface.projectPath)
        assertEquals(
            "src/main/java/dev/erst/fingrind/cli/json/Example.java",
            surface.relativePath,
        )
        assertEquals("example-reviewed-surface", surface.reviewedRoleName)
        assertEquals("Example variance.", surface.budgetVarianceReason)
    }

    @Test
    fun javaReviewedSurfaces_rejectDuplicateKeysAcrossRegistryFragments() {
        val repositoryRoot = Files.createTempDirectory("reviewed-surface-registry-duplicate")
        val firstPath =
            writeJavaFragment(
                repositoryRoot = repositoryRoot,
                fileName = "java/first.json",
                body =
                    """
                    {
                      "projectPath": "cli",
                      "relativePath": "src/main/java/dev/erst/fingrind/cli/json/Example.java",
                      "owner": "example-owner",
                      "reason": "Example reason.",
                      "splitTrigger": "Split the example owner.",
                      "reviewedRoleName": "first-reviewed-surface",
                      "budgetVarianceReason": "Example variance.",
                      "approval": {
                        "physicalLines": 10,
                        "logicalLines": 9,
                        "importLikeLines": 1,
                        "nestedTypes": 1,
                        "functions": 2,
                        "fieldsPerTopLevelType": 1,
                        "switchArmsPerMethod": 0,
                        "methodLineSpan": 6,
                        "methodParameters": 2,
                        "methodDecisionPoints": 1,
                        "expiresOn": "2026-08-16"
                      }
                    }
                    """.trimIndent(),
            )
        val duplicatePath =
            writeJavaFragment(
                repositoryRoot = repositoryRoot,
                fileName = "java/duplicate.json",
                body =
                    """
                    {
                      "projectPath": "cli",
                      "relativePath": "src/main/java/dev/erst/fingrind/cli/json/Example.java",
                      "owner": "example-owner",
                      "reason": "Example reason.",
                      "splitTrigger": "Split the example owner.",
                      "reviewedRoleName": "duplicate-reviewed-surface",
                      "budgetVarianceReason": "Example variance.",
                      "approval": {
                        "physicalLines": 10,
                        "logicalLines": 9,
                        "importLikeLines": 1,
                        "nestedTypes": 1,
                        "functions": 2,
                        "fieldsPerTopLevelType": 1,
                        "switchArmsPerMethod": 0,
                        "methodLineSpan": 6,
                        "methodParameters": 2,
                        "methodDecisionPoints": 1,
                        "expiresOn": "2026-08-16"
                      }
                    }
                    """.trimIndent(),
            )
        writeJavaFragment(
            repositoryRoot = repositoryRoot,
            fileName = "text/example.json",
            body =
                """
                {
                  "relativePath": "sqlite/src/main/resources/dev/erst/fingrind/sqlite/example.sql",
                  "owner": "sqlite-example",
                  "reason": "Example text reason.",
                  "splitTrigger": "Split the example text owner.",
                  "reviewedRoleName": "sqlite-example-surface",
                  "budgetVarianceReason": "Example text variance.",
                  "approval": {
                    "physicalLines": 20,
                    "logicalLines": 19,
                    "importLikeLines": 1,
                    "functions": 0,
                    "nestedTypes": 0,
                    "expiresOn": "2026-08-16"
                  }
                }
                """.trimIndent(),
        )

        val exception =
            assertFailsWith<IllegalStateException> {
                ReviewedSurfaceRegistry.javaReviewedSurfaces(repositoryRoot)
            }

        assertTrue(exception.message.orEmpty().contains(pathText(firstPath)))
        assertTrue(exception.message.orEmpty().contains(pathText(duplicatePath)))
        assertTrue(
            exception.message.orEmpty().contains(
                "cli/src/main/java/dev/erst/fingrind/cli/json/Example.java",
            ),
        )
    }

    private fun writeJavaFragment(
        repositoryRoot: Path,
        fileName: String,
        body: String,
    ): Path {
        val filePath =
            repositoryRoot
                .resolve("scripts/structural_governance/reviewed_surface_registry")
                .resolve(fileName)
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, "$body\n")
        return filePath
    }

    private fun pathText(path: Path): String = path.toString().replace('\\', '/')
}
