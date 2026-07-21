package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies public-safe failures emitted while SQLite constructs protected-book backup stages. */
class SqliteProtectedBookStagingFilesTest extends SqliteNativeBridgeTestSupport {

  @Test
  void backupExportFailure_preservesTheCheckpointAndExposesOnlyNativeResultNames() {
    SqliteProtectedBookStagingFiles.BackupExportFailure nativeFailure =
        new SqliteProtectedBookStagingFiles.BackupExportFailure(
            SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_COPY,
            new SqliteNativeException(
                SqliteNativeResultCode.code("BUSY"), "private native detail"));

    assertEquals(
        SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_COPY,
        nativeFailure.checkpoint());
    assertEquals(
        SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_COPY.failureMessage()
            + " SQLite reported SQLITE_BUSY.",
        nativeFailure.publicFailureMessage());

    SqliteProtectedBookStagingFiles.BackupExportFailure filesystemFailure =
        new SqliteProtectedBookStagingFiles.BackupExportFailure(
            SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_HARDEN,
            new IllegalStateException("private filesystem detail"));

    assertEquals(
        SqliteProtectedBookStagingSupport.StagingCheckpoint.BACKUP_HARDEN.failureMessage(),
        filesystemFailure.publicFailureMessage());
  }

  @Test
  void stagingFileHelpers_enforceRegularInputsAndSecureTheirOwnedArtifacts() throws Exception {
    Path regularBook = tempDirectory.resolve("regular-book.sqlite");
    Files.writeString(regularBook, "protected-book-fixture");
    assertDoesNotThrow(
        () -> SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(regularBook));
    assertThrows(
        SqliteCallerPathContractException.class,
        () -> SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(tempDirectory));

    Path backupArtifact = tempDirectory.resolve("backup-parent").resolve("backup.fgba");
    Path backupKey = tempDirectory.resolve("backup-key-parent").resolve("backup.key");
    SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(backupArtifact);
    SqliteProtectedBookStagingFiles.ensureSecureBackupKeyFileParentDirectory(backupKey);
    assertDoesNotThrow(() -> SqliteProtectedBookStagingFiles.hardenBookArtifacts(regularBook));

    Path stagedSecret = tempDirectory.resolve("staged.key");
    Files.writeString(stagedSecret, "transient secret");
    SqliteProtectedBookStagingFiles.resetStagedSecretFile(stagedSecret);
    assertFalse(Files.exists(stagedSecret));
  }
}
