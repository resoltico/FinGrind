package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Field-tests cleanup of protected-book files created by a rolled-back ledger plan. */
class SqliteLedgerPlanArtifactCleanupTest extends SqliteNativeBridgeTestSupport {
  @Test
  void cleanup_removesCreatedBookArtifactsAndOnlyTheDirectoriesItCreated() throws Exception {
    Path boundary = tempDirectory.resolve("boundary");
    Path createdDirectory = boundary.resolve("created").resolve("nested");
    Path bookPath = createdDirectory.resolve("plan.sqlite");
    Files.createDirectories(createdDirectory);
    Files.writeString(bookPath, "book");
    Files.writeString(bookPath.resolveSibling("plan.sqlite-journal"), "journal");
    Files.writeString(bookPath.resolveSibling("plan.sqlite-wal"), "wal");
    Files.writeString(bookPath.resolveSibling("plan.sqlite-shm"), "shm");

    SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(bookPath, boundary, null);

    assertFalse(Files.exists(bookPath));
    assertFalse(Files.exists(bookPath.resolveSibling("plan.sqlite-journal")));
    assertFalse(Files.exists(bookPath.resolveSibling("plan.sqlite-wal")));
    assertFalse(Files.exists(bookPath.resolveSibling("plan.sqlite-shm")));
    assertFalse(Files.exists(createdDirectory));
    assertTrue(Files.isDirectory(boundary));
  }

  @Test
  void parentCleanup_toleratesMissingDirectoriesAndPreservesNonemptyDirectories() throws Exception {
    Path boundary = tempDirectory.resolve("boundary");
    Path missingDirectory = boundary.resolve("missing");
    Files.createDirectories(boundary);

    SqliteLedgerPlanArtifactCleanup.deleteEmptyCreatedParentDirectories(missingDirectory, boundary);
    assertTrue(Files.isDirectory(boundary));

    Path nonemptyDirectory = boundary.resolve("nonempty");
    Files.createDirectories(nonemptyDirectory);
    Files.writeString(nonemptyDirectory.resolve("retained.txt"), "retained");
    SqliteLedgerPlanArtifactCleanup.deleteEmptyCreatedParentDirectories(
        nonemptyDirectory, boundary);

    assertTrue(Files.isDirectory(nonemptyDirectory));
    assertTrue(Files.exists(nonemptyDirectory.resolve("retained.txt")));
    assertEquals(
        boundary, SqliteLedgerPlanArtifactCleanup.nearestExistingAncestor(missingDirectory));
  }

  @Test
  void cleanup_reportsUndeletableArtifactsAndDatabaseCloseFailures() throws Exception {
    Path nonemptyDirectory = tempDirectory.resolve("nonempty-artifact");
    Files.createDirectories(nonemptyDirectory);
    Files.writeString(nonemptyDirectory.resolve("retained.txt"), "retained");

    IllegalStateException deletionFailure =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteLedgerPlanArtifactCleanup.deleteBookArtifactIfPresent(nonemptyDirectory));
    assertTrue(
        java.util.Objects.requireNonNullElse(deletionFailure.getMessage(), "")
            .contains("Failed to remove SQLite book artifact"));

    RuntimeException closeCause = new IllegalStateException("close failure");
    SqliteNativeDatabase failingDatabase =
        new SqliteNativeDatabase(MemorySegment.NULL) {
          @Override
          public void close() {
            throw closeCause;
          }
        };
    IllegalStateException closeFailure =
        assertThrows(
            IllegalStateException.class,
            () -> SqliteLedgerPlanArtifactCleanup.closeCreatedCleanupDatabase(failingDatabase));
    assertSame(closeCause, closeFailure.getCause());
    assertDoesNotThrow(() -> SqliteLedgerPlanArtifactCleanup.closeCreatedCleanupDatabase(null));
  }
}
