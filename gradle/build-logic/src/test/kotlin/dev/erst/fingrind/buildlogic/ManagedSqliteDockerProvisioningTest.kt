package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.gradle.testfixtures.ProjectBuilder

class ManagedSqliteDockerProvisioningTest {
    @Test
    fun dockerContextBuildsDockerOwnedManagedSqliteWhenClassifierMatchesHost() {
        val repositoryRoot = Files.createTempDirectory("managed-sqlite-docker-provisioning")
        try {
            DistributionContractReaderTestSupport.writeContractResource(
                repositoryRoot,
                "contract-schema-keys.json",
                DistributionContractReaderTestSupport.contractSchemaKeysJson(),
            )
            DistributionContractReaderTestSupport.writeContractResource(
                repositoryRoot,
                "managed-sqlite-contract.json",
                DistributionContractReaderTestSupport.managedSqliteContractJson("unused", null),
            )
            DistributionContractReaderTestSupport.writeContractResource(
                repositoryRoot,
                "bundle-layout-contract.json",
                """
                {
                  "bundleTargets": {
                    "linux-x86_64": {
                      "operatingSystemId": "linux",
                      "architectureId": "x86_64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.so.0",
                      "compatibilityLabel": "glibc 2.34+ Linux x86_64",
                      "minimumGlibcVersion": "2.34",
                      "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                    },
                    "linux-aarch64": {
                      "operatingSystemId": "linux",
                      "architectureId": "aarch64",
                      "archiveFormat": "tar.gz",
                      "launcherPath": "bin/fingrind",
                      "launcherCommand": "./bin/fingrind",
                      "sqliteLibraryFileName": "libsqlite3.so.0",
                      "compatibilityLabel": "glibc 2.34+ Linux aarch64",
                      "minimumGlibcVersion": "2.34",
                      "compatibilitySmokeContainerImage": "rockylinux:9@sha256:floor-proof"
                    }
                  }
                }
                """.trimIndent(),
            )

            val sqliteSourceDirectory = repositoryRoot.resolve("third_party/sqlite").createDirectories()
            val project = ProjectBuilder.builder().withProjectDir(repositoryRoot.toFile()).build()
            val hostPrepareTask = project.tasks.register("prepareManagedSqliteHost")
            val hostProvisioning =
                ManagedSqliteProvisioning(
                    classifier = "linux-x86_64",
                    libraryFileName = "libsqlite3.so.0",
                    libraryPath =
                        project.layout.buildDirectory.file(
                            "managed-sqlite/linux-x86_64/libsqlite3.so.0",
                        ),
                    checksumPath =
                        project.layout.buildDirectory.file(
                            "managed-sqlite/linux-x86_64/libsqlite3.so.0.sha256",
                        ),
                    toolchainFingerprintPath =
                        project.layout.buildDirectory.file(
                            "managed-sqlite/linux-x86_64/toolchain-fingerprint.json",
                        ),
                    buildContractPath =
                        project.layout.buildDirectory.file(
                            "managed-sqlite/linux-x86_64/build-contract.json",
                        ),
                    prepareTask = hostPrepareTask,
                )

            val dockerProvisioning =
                registerDockerManagedSqliteTarget(
                    project = project,
                    hostProvisioning = hostProvisioning,
                    repositoryRootDirectory = repositoryRoot,
                    sqliteSourceDirectory = project.layout.dir(project.provider { sqliteSourceDirectory.toFile() }).get(),
                    sqliteVersionValue = "3.53.4",
                    sqlite3mcVersionValue = "2.4.0",
                    sourcePackageId = "sqlite3mc-amalgamation-test",
                    dockerBundleTarget =
                        BundleTargetContract(
                            classifier = "linux-x86_64",
                            operatingSystemId = "linux",
                            architectureId = "x86_64",
                            archiveFormat = "tar.gz",
                            launcherPath = "bin/fingrind",
                            launcherCommand = "./bin/fingrind",
                            sqliteLibraryFileName = "libsqlite3.so.0",
                            compatibilityLabel = "glibc 2.34+ Linux x86_64",
                            minimumGlibcVersion = "2.34",
                            compatibilitySmokeContainerImage = "rockylinux:9@sha256:floor-proof",
                            publicBundlePublication =
                                PublicBundlePublicationContract(
                                    status = "published",
                                ),
                        ),
                )

            assertEquals("linux-x86_64", dockerProvisioning.classifier)
            assertEquals("prepareDockerManagedSqlite", dockerProvisioning.prepareTask.name)
            assertEquals(
                project.layout.buildDirectory.file(
                    "managed-sqlite/docker-context/linux-x86_64/libsqlite3.so.0",
                ).get().asFile,
                dockerProvisioning.libraryPath.get().asFile,
            )
            assertNotEquals(
                hostProvisioning.libraryPath.get().asFile,
                dockerProvisioning.libraryPath.get().asFile,
            )
        } finally {
            DistributionContractReaderTestSupport.deleteTree(repositoryRoot)
        }
    }
}
