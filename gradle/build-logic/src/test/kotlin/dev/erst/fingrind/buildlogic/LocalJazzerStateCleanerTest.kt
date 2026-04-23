package dev.erst.fingrind.buildlogic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalJazzerStateCleanerTest {
    @Test
    fun deleteGeneratedCorpora_removesNestedCorpusTreesWithoutTouchingOtherLocalState() {
        val localDirectory = Files.createTempDirectory("local-jazzer-state")
        try {
            val corpusRoot = localDirectory.resolve("runs/ledger-plan-request/.cifuzz-corpus")
            writeFile(corpusRoot.resolve("queue/id_000001"), "seed")
            writeFile(corpusRoot.resolve("artifacts/minimized/input.json"), "{}")
            val latestLog = localDirectory.resolve("runs/ledger-plan-request/latest.log")
            val scratchNote = localDirectory.resolve("notes/keep.txt")
            writeFile(latestLog, "run-log")
            writeFile(scratchNote, "keep")

            LocalJazzerStateCleaner.deleteGeneratedCorpora(localDirectory)

            assertFalse(Files.exists(corpusRoot))
            assertTrue(Files.exists(latestLog))
            assertTrue(Files.exists(scratchNote))
        } finally {
            deleteTree(localDirectory)
        }
    }

    @Test
    fun deleteRunFindings_preservesCorporaButRemovesCrashFilesAndHistory() {
        val runsDirectory = Files.createTempDirectory("jazzer-runs")
        try {
            val corpusSeed = runsDirectory.resolve("ledger-plan-request/.cifuzz-corpus/queue/id_000001")
            val latestLog = runsDirectory.resolve("ledger-plan-request/latest.log")
            val historyLog = runsDirectory.resolve("ledger-plan-request/history/20260423T120000Z/run.log")
            val crashFile = runsDirectory.resolve("posting-workflow/crash-000001.txt")
            writeFile(corpusSeed, "seed")
            writeFile(latestLog, "latest")
            writeFile(historyLog, "history")
            writeFile(crashFile, "crash")

            LocalJazzerStateCleaner.deleteRunFindings(runsDirectory)

            assertTrue(Files.exists(corpusSeed))
            assertFalse(Files.exists(latestLog))
            assertFalse(Files.exists(historyLog.parent))
            assertFalse(Files.exists(crashFile.parent))
        } finally {
            deleteTree(runsDirectory)
        }
    }

    @Test
    fun deleteRunFindings_skipsUnreadableCorpusSubtrees() {
        val runsDirectory = Files.createTempDirectory("jazzer-runs")
        val unreadableCorpusRoot = runsDirectory.resolve("sqlite-book-roundtrip/.cifuzz-corpus")
        try {
            writeFile(unreadableCorpusRoot.resolve("queue/id_000001"), "seed")
            val latestLog = runsDirectory.resolve("sqlite-book-roundtrip/latest.log")
            writeFile(latestLog, "latest")
            Files.setPosixFilePermissions(
                unreadableCorpusRoot,
                setOf(PosixFilePermission.OWNER_WRITE),
            )

            LocalJazzerStateCleaner.deleteRunFindings(runsDirectory)

            assertTrue(Files.exists(unreadableCorpusRoot))
            assertFalse(Files.exists(latestLog))
        } finally {
            if (Files.exists(unreadableCorpusRoot)) {
                Files.setPosixFilePermissions(
                    unreadableCorpusRoot,
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    ),
                )
            }
            deleteTree(runsDirectory)
        }
    }

    @Test
    fun deleteGeneratedCorpora_warnsButContinuesWhenOneCorpusRootIsUnreadable() {
        val localDirectory = Files.createTempDirectory("local-jazzer-state")
        val unreadableCorpusRoot = localDirectory.resolve("runs/sqlite-book-roundtrip/.cifuzz-corpus")
        val warnings = mutableListOf<String>()
        try {
            val removableCorpusRoot = localDirectory.resolve("runs/ledger-plan-request/.cifuzz-corpus")
            writeFile(removableCorpusRoot.resolve("queue/id_000001"), "seed")
            writeFile(unreadableCorpusRoot.resolve("queue/id_000001"), "seed")
            Files.setPosixFilePermissions(
                unreadableCorpusRoot,
                setOf(PosixFilePermission.OWNER_WRITE),
            )

            LocalJazzerStateCleaner.deleteGeneratedCorpora(localDirectory, warnings::add)

            assertFalse(Files.exists(removableCorpusRoot))
            assertTrue(Files.exists(unreadableCorpusRoot))
            assertTrue(warnings.isNotEmpty())
        } finally {
            if (Files.exists(unreadableCorpusRoot)) {
                Files.setPosixFilePermissions(
                    unreadableCorpusRoot,
                    setOf(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE,
                    ),
                )
            }
            deleteTree(localDirectory)
        }
    }

    private fun writeFile(path: Path, contents: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, contents)
    }

    private fun deleteTree(rootPath: Path) {
        if (!Files.exists(rootPath)) {
            return
        }
        Files.walk(rootPath).use { pathStream ->
            pathStream.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }
}
