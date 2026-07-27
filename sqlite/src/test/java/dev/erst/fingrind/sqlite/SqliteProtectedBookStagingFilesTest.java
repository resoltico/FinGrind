package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies no-create parent validation at protected-book backup staging boundaries. */
class SqliteProtectedBookStagingFilesTest {
  @TempDir Path tempDirectory;

  @Test
  void existingBackupArtifactParentsAreRejectedWithoutCreatingThem() {
    Path missingParentArtifact = tempDirectory.resolve("missing-book-parent/backup.sqlite");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteProtectedBookStagingFiles.requireExistingSecureBackupFileParentDirectory(
                    missingParentArtifact));

    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, failure.pathFailure());
  }

  @Test
  void existingBackupKeyParentsAreRejectedWithoutCreatingThem() {
    Path missingParentArtifact = tempDirectory.resolve("missing-key-parent/backup.key");

    SqliteCallerPathContractException failure =
        assertThrows(
            SqliteCallerPathContractException.class,
            () ->
                SqliteProtectedBookStagingFiles.requireExistingSecureBackupKeyFileParentDirectory(
                    missingParentArtifact));

    assertEquals(SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY, failure.pathFailure());
  }
}
