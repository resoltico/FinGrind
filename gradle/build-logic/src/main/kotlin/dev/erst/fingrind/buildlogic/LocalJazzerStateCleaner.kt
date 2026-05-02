package dev.erst.fingrind.buildlogic

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal object LocalJazzerStateCleaner {
    private const val CORPUS_DIRECTORY_NAME = ".cifuzz-corpus"
    internal fun interface DeleteOperations {
        @Throws(IOException::class)
        fun deleteIfExists(path: Path): Boolean
    }

    private val fileSystemDeleteOperations = DeleteOperations { path -> Files.deleteIfExists(path) }

    fun deleteGeneratedCorpora(localPath: Path, warningConsumer: (String) -> Unit = {}) {
        deleteGeneratedCorpora(localPath, fileSystemDeleteOperations, warningConsumer)
    }

    internal fun deleteGeneratedCorpora(
        localPath: Path,
        deleteOperations: DeleteOperations,
        warningConsumer: (String) -> Unit,
    ) {
        if (!Files.exists(localPath)) {
            return
        }
        val corpusRoots = mutableListOf<Path>()
        Files.walkFileTree(
            localPath,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ) = if (dir.fileName?.toString() == CORPUS_DIRECTORY_NAME) {
                    corpusRoots.add(dir)
                    java.nio.file.FileVisitResult.SKIP_SUBTREE
                } else {
                    java.nio.file.FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exception: IOException,
                ): java.nio.file.FileVisitResult {
                    if (file.fileName?.toString() == CORPUS_DIRECTORY_NAME) {
                        corpusRoots.add(file)
                    }
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            },
        )
        corpusRoots.forEach { corpusRoot -> deleteCorpusTree(corpusRoot, deleteOperations, warningConsumer) }
    }

    fun deleteRunFindings(runsPath: Path, warningConsumer: (String) -> Unit = {}) {
        if (!Files.exists(runsPath)) {
            return
        }
        Files.walkFileTree(
            runsPath,
            object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(
                    dir: Path,
                    attrs: BasicFileAttributes,
                ) = if (dir != runsPath && dir.fileName?.toString() == CORPUS_DIRECTORY_NAME) {
                    java.nio.file.FileVisitResult.SKIP_SUBTREE
                } else {
                    java.nio.file.FileVisitResult.CONTINUE
                }

                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes,
                ): java.nio.file.FileVisitResult {
                    deleteFile(file, fileSystemDeleteOperations, warningConsumer)
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exception: IOException,
                ): java.nio.file.FileVisitResult {
                    if (!isUnderCorpus(file)) {
                        deleteFile(file, fileSystemDeleteOperations, warningConsumer)
                    }
                    return java.nio.file.FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(
                    dir: Path,
                    exception: IOException?,
                ): java.nio.file.FileVisitResult {
                    if (dir == runsPath || dir.fileName?.toString() == CORPUS_DIRECTORY_NAME) {
                        return java.nio.file.FileVisitResult.CONTINUE
                    }
                    deleteFindingDirectory(dir, warningConsumer, exception)
                    return java.nio.file.FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun deleteCorpusTree(
        corpusRoot: Path,
        deleteOperations: DeleteOperations,
        warningConsumer: (String) -> Unit,
    ) {
        try {
            Files.walkFileTree(
                corpusRoot,
                object : SimpleFileVisitor<Path>() {
                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): java.nio.file.FileVisitResult {
                        deleteFile(file, deleteOperations, warningConsumer)
                        return java.nio.file.FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(
                        file: Path,
                        exception: IOException,
                    ): java.nio.file.FileVisitResult {
                        deletePath(file, deleteOperations, warningConsumer)
                        return java.nio.file.FileVisitResult.CONTINUE
                    }

                    override fun postVisitDirectory(
                        dir: Path,
                        exception: IOException?,
                    ): java.nio.file.FileVisitResult {
                        if (exception != null) {
                            warningConsumer(
                                "Unable to fully inspect local Jazzer corpus path '$dir': ${exception.message}",
                            )
                        }
                        deletePath(dir, deleteOperations, warningConsumer)
                        return java.nio.file.FileVisitResult.CONTINUE
                    }
                },
            )
        } catch (exception: IOException) {
            warningConsumer(
                "Unable to clean local Jazzer corpus root '$corpusRoot': ${exception.message}",
            )
        }
    }

    private fun deleteFile(
        path: Path,
        deleteOperations: DeleteOperations,
        warningConsumer: (String) -> Unit,
    ) {
        try {
            deleteOperations.deleteIfExists(path)
        } catch (exception: IOException) {
            warningConsumer("Unable to delete local Jazzer file '$path': ${exception.message}")
        }
    }

    private fun deleteFindingDirectory(
        path: Path,
        warningConsumer: (String) -> Unit,
        exception: IOException?,
    ) {
        if (exception != null) {
            warningConsumer(
                "Unable to fully inspect local Jazzer run directory '$path': ${exception.message}",
            )
        }
        try {
            Files.deleteIfExists(path)
        } catch (_: DirectoryNotEmptyException) {
            // Preserved corpus content intentionally keeps some run directories alive.
        } catch (_: AccessDeniedException) {
            // Some filesystems surface preserved corpus descendants as access denied instead.
        } catch (deleteException: IOException) {
            warningConsumer(
                "Unable to delete local Jazzer run directory '$path': ${deleteException.message}",
            )
        }
    }

    private fun deletePath(
        path: Path,
        deleteOperations: DeleteOperations,
        warningConsumer: (String) -> Unit,
    ) {
        try {
            deleteOperations.deleteIfExists(path)
        } catch (_: DirectoryNotEmptyException) {
            warningConsumer("Local Jazzer corpus path '$path' still contains undeletable entries.")
        } catch (_: AccessDeniedException) {
            warningConsumer("Local Jazzer corpus path '$path' is not currently deletable.")
        } catch (exception: IOException) {
            warningConsumer(
                "Unable to delete local Jazzer corpus path '$path': ${exception.message}",
            )
        }
    }

    private fun isUnderCorpus(path: Path): Boolean =
        path.iterator().asSequence().any { segment -> segment.toString() == CORPUS_DIRECTORY_NAME }
}
