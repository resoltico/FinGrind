package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Verifies public-safe failures emitted while SQLite constructs protected-book backup stages. */
class SqliteProtectedBookStagingFilesTest {

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
}
