package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RecoverRekeyResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyRecoveryAction;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavioral tests for closed-copy backup, restore, and rekey-recovery services. */
class SqliteBookMaintenanceServiceTest extends SqliteNativeBridgeTestSupport {
  private static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (bookFilePath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError(
                    "These service tests expect key-file-backed book access only.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError(
                    "These service tests expect key-file-backed book access only.");
          };

  @Test
  void backupBook_createsClosedCopyBackupPairThatOpensWithTheMaterializedKeyFile()
      throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("entity.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(bookAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("entity.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("entity.key");

    BackupBookResult.BackedUp result =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            new SqliteBookBackupService()
                .backupBook(bookAccess, backupFilePath, backupBookKeyFilePath, KEY_FILE_RESOLVER)
                .requireAccepted());

    assertEquals(bookPath.toAbsolutePath().normalize(), result.bookFilePath());
    assertEquals(backupFilePath.toAbsolutePath().normalize(), result.backupFilePath());
    assertEquals(
        backupBookKeyFilePath.toAbsolutePath().normalize(), result.backupBookKeyFilePath());
    assertTrue(Files.exists(backupFilePath));
    assertTrue(Files.exists(backupBookKeyFilePath));
    assertEquals(
        1,
        sampleRowCount(
            new BookAccess(
                backupFilePath, new BookAccess.PassphraseSource.KeyFile(backupBookKeyFilePath))));
  }

  @Test
  void backupBook_rejectsLiveBookPathsWithBlockingArtifacts() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("sidecar.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(bookAccess);
    Files.writeString(bookPath.resolveSibling("sidecar.sqlite-wal"), "blocked");

    BackupBookResult.Rejected result =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            new SqliteBookBackupService()
                .backupBook(
                    bookAccess,
                    tempDirectory.resolve("backup").resolve("sidecar.sqlite"),
                    tempDirectory.resolve("backup").resolve("sidecar.key"),
                    KEY_FILE_RESOLVER)
                .requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts rejection =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, result.rejection());
    assertEquals(bookPath.toAbsolutePath().normalize(), rejection.bookFilePath());
    assertTrue(
        rejection.blockingArtifactPaths().stream()
            .anyMatch(path -> path.getFileName().toString().endsWith("-wal")));
  }

  @Test
  void backupBook_rejectsExistingBackupDestinationsAndKeyDestinations() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("duplicate-targets.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(bookAccess);

    Path existingBackupFilePath = tempDirectory.resolve("backup").resolve("duplicate.sqlite");
    Files.createDirectories(existingBackupFilePath.getParent());
    Files.writeString(existingBackupFilePath, "existing backup");
    BackupBookResult.Rejected existingBackupResult =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            new SqliteBookBackupService()
                .backupBook(
                    bookAccess,
                    existingBackupFilePath,
                    tempDirectory.resolve("backup").resolve("duplicate.key"),
                    KEY_FILE_RESOLVER)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        existingBackupResult.rejection());

    Path backupFilePath = tempDirectory.resolve("backup").resolve("duplicate-2.sqlite");
    Path existingBackupKeyFilePath = tempDirectory.resolve("backup").resolve("duplicate-2.key");
    Files.writeString(existingBackupKeyFilePath, "existing key");
    BackupBookResult.Rejected existingKeyResult =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            new SqliteBookBackupService()
                .backupBook(
                    bookAccess, backupFilePath, existingBackupKeyFilePath, KEY_FILE_RESOLVER)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.BackupKeyFileAlreadyExists.class, existingKeyResult.rejection());
  }

  @Test
  void backupBook_deletesMaterializedBackupKeyFileWhenBookCopyFails() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("copy-failure.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(bookAccess);
    Path backupParentFile = tempDirectory.resolve("blocked-parent");
    Files.writeString(backupParentFile, "not-a-directory");
    Path backupFilePath = backupParentFile.resolve("copy-failure.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("copy-failure.key");

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqliteBookBackupService()
                    .backupBook(
                        bookAccess, backupFilePath, backupBookKeyFilePath, KEY_FILE_RESOLVER));

    assertTrue(
        NullTestSupport.messageOf(exception).contains("existing directory"),
        () -> "Expected directory failure, got: " + NullTestSupport.messageOf(exception));
    assertFalse(Files.exists(backupBookKeyFilePath));
  }

  @Test
  void restoreBook_replacesTheLiveBookFromTheVerifiedBackupPair() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("restore.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("restore.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("restore.key");
    new SqliteBookBackupService()
        .backupBook(liveBookAccess, backupFilePath, backupBookKeyFilePath, KEY_FILE_RESOLVER)
        .requireAccepted();
    appendSampleRow(liveBookAccess, 2, "mutated");
    assertEquals(2, sampleRowCount(liveBookAccess));

    RestoreBookResult.Restored result =
        assertInstanceOf(
            RestoreBookResult.Restored.class,
            new SqliteBookRestoreService()
                .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath, KEY_FILE_RESOLVER)
                .requireAccepted());

    assertEquals(bookPath.toAbsolutePath().normalize(), result.bookFilePath());
    assertEquals(backupFilePath.toAbsolutePath().normalize(), result.backupFilePath());
    assertEquals(
        backupBookKeyFilePath.toAbsolutePath().normalize(), result.backupBookKeyFilePath());
    assertEquals(
        1,
        sampleRowCount(
            new BookAccess(
                bookPath, new BookAccess.PassphraseSource.KeyFile(backupBookKeyFilePath))));
  }

  @Test
  void restoreBook_rejectsBlockingArtifactsOnLiveBookAndBackupSource() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("restore-blocked.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("restore-blocked.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("restore-blocked.key");
    new SqliteBookBackupService()
        .backupBook(liveBookAccess, backupFilePath, backupBookKeyFilePath, KEY_FILE_RESOLVER)
        .requireAccepted();

    Files.writeString(bookPath.resolveSibling("restore-blocked.sqlite-wal"), "live sidecar");
    RestoreBookResult.Rejected liveBlocked =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            new SqliteBookRestoreService()
                .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath, KEY_FILE_RESOLVER)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.BookHasBlockingArtifacts.class, liveBlocked.rejection());
    Files.deleteIfExists(bookPath.resolveSibling("restore-blocked.sqlite-wal"));

    Files.writeString(
        backupFilePath.resolveSibling("restore-blocked.sqlite-wal"), "backup sidecar");
    RestoreBookResult.Rejected backupBlocked =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            new SqliteBookRestoreService()
                .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath, KEY_FILE_RESOLVER)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.BackupSourceHasBlockingArtifacts.class, backupBlocked.rejection());
  }

  @Test
  void recoverRekey_inspectsAndRestoresTheSingleSiblingRollbackArtifact() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("recover.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());
    appendSampleRow(liveBookAccess, 2, "mutated");
    assertEquals(2, sampleRowCount(liveBookAccess));

    RecoverRekeyResult.Inspected inspected =
        assertInstanceOf(
            RecoverRekeyResult.Inspected.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.INSPECT, null)
                .requireAccepted());
    assertEquals(
        java.util.List.of(rollbackFile.path().toAbsolutePath().normalize()),
        inspected.rollbackArtifactPaths());

    RecoverRekeyResult.Restored restored =
        assertInstanceOf(
            RecoverRekeyResult.Restored.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.RESTORE, null)
                .requireAccepted());
    assertEquals(rollbackFile.path().toAbsolutePath().normalize(), restored.rollbackArtifactPath());
    assertEquals(1, sampleRowCount(liveBookAccess));
  }

  @Test
  void recoverRekey_deleteRemovesTheSelectedRollbackArtifact() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("delete-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());

    RecoverRekeyResult.Deleted deleted =
        assertInstanceOf(
            RecoverRekeyResult.Deleted.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.DELETE, rollbackFile.path())
                .requireAccepted());

    assertEquals(rollbackFile.path().toAbsolutePath().normalize(), deleted.rollbackArtifactPath());
    assertFalse(Files.exists(rollbackFile.path()));
  }

  @Test
  void recoverRekey_rejectsWhenNoRollbackArtifactsExist() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("missing-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);

    RecoverRekeyResult.Rejected rejected =
        assertInstanceOf(
            RecoverRekeyResult.Rejected.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.RESTORE, null)
                .requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.NoRollbackArtifactsFound.class, rejected.rejection());
  }

  @Test
  void recoverRekey_requiresExplicitSelectionWhenMultipleArtifactsExist() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("multiple-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());
    SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());

    RecoverRekeyResult.Rejected rejected =
        assertInstanceOf(
            RecoverRekeyResult.Rejected.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.RESTORE, null)
                .requireAccepted());

    BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired =
        assertInstanceOf(
            BookMaintenanceRejection.RollbackArtifactSelectionRequired.class, rejected.rejection());
    assertEquals(2, selectionRequired.rollbackArtifactPaths().size());
  }

  @Test
  void recoverRekey_rejectsMissingOrMismatchedExplicitRollbackArtifacts() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("explicit-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    Path missingRollbackArtifact =
        tempDirectory.resolve("books").resolve("missing-rollback.sqlite");

    RecoverRekeyResult.Rejected missingRejected =
        assertInstanceOf(
            RecoverRekeyResult.Rejected.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.DELETE, missingRollbackArtifact)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactNotFound.class, missingRejected.rejection());

    Path otherBookPath = tempDirectory.resolve("books").resolve("other.sqlite");
    BookAccess otherBookAccess = bookAccess(otherBookPath);
    initializeBookWithSampleRow(otherBookAccess);
    SqliteRekeyRollbackFile otherRollbackArtifact =
        SqliteRekeyRollbackFile.create(otherBookPath.toAbsolutePath().normalize());

    RecoverRekeyResult.Rejected mismatchRejected =
        assertInstanceOf(
            RecoverRekeyResult.Rejected.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.RESTORE, otherRollbackArtifact.path())
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactNotForBook.class, mismatchRejected.rejection());
  }

  @Test
  void recoverRekey_restoreRejectsNonRollbackBlockingArtifacts() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("blocked-restore.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());
    Files.writeString(bookPath.resolveSibling("blocked-restore.sqlite-wal"), "busy");

    RecoverRekeyResult.Rejected rejected =
        assertInstanceOf(
            RecoverRekeyResult.Rejected.class,
            new SqliteRekeyRecoveryService()
                .recover(bookPath, RekeyRecoveryAction.RESTORE, rollbackFile.path())
                .requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(
        Set.of(bookPath.resolveSibling("blocked-restore.sqlite-wal").toAbsolutePath().normalize()),
        Set.copyOf(blockingArtifacts.blockingArtifactPaths()));
  }

  @Test
  void recoverRekey_wrapsRollbackScanFailuresAsIllegalState() {
    try (AclFixtureFileSystem fileSystem = AclFixtureFileSystem.withViews(Set.of("basic"))) {
      AclFixturePath parentPath = fileSystem.path("\\books");
      parentPath.exists = true;
      parentPath.regularFile = false;
      parentPath.failNewDirectoryStreamWith(new IOException("scan-boom"));
      AclFixturePath bookPath = fileSystem.path("\\books\\acme.sqlite");
      bookPath.exists = true;
      bookPath.regularFile = true;

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  new SqliteRekeyRecoveryService()
                      .recover(bookPath, RekeyRecoveryAction.INSPECT, null));
      assertTrue(NullTestSupport.messageOf(exception).contains("rollback artifacts"));
      assertEquals("scan-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  private void initializeBookWithSampleRow(BookAccess bookAccess) {
    try {
      Path parentDirectory = bookAccess.bookFilePath().getParent();
      if (parentDirectory != null) {
        Files.createDirectories(parentDirectory);
        SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parentDirectory);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create the test book directory.", exception);
    }
    withOpenDatabase(
        bookAccess,
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          SqliteStoreFixtureSupport.insertCanonicalInitializedBookMetadata(database);
          database.executeStatement(
              "create table sample (id integer primary key, note text not null)");
          database.executeStatement("insert into sample (id, note) values (1, 'seed')");
        });
  }

  private void appendSampleRow(BookAccess bookAccess, int id, String note) {
    withOpenDatabase(
        bookAccess,
        database ->
            database.executeStatement(
                "insert into sample (id, note) values (%d, '%s')".formatted(id, note)));
  }

  private int sampleRowCount(BookAccess bookAccess) {
    final int[] count = new int[1];
    withOpenDatabase(
        bookAccess,
        database -> {
          try (SqliteNativeStatement statement =
              SqliteNativeStatements.prepare(database, "select count(*) from sample")) {
            assertEquals(SqliteNativeResultCodes.ROW, statement.step());
            count[0] = statement.columnInt(0);
            assertEquals(SqliteNativeResultCodes.DONE, statement.step());
          }
        });
    return count[0];
  }
}
