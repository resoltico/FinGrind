package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.ProtectedBookMaintenanceService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Behavioral tests for the executor-owned protected-book maintenance service on SQLite. */
class SqliteProtectedBookMaintenanceServiceTest extends SqliteNativeBridgeTestSupport {
  private static final SqlitePassphraseResolver KEY_FILE_RESOLVER =
      (bookFilePath, passphraseSource, intent) ->
          switch (passphraseSource) {
            case BookAccess.PassphraseSource.KeyFile keyFile ->
                SqliteBookKeyFile.loadDecision(keyFile.bookKeyFilePath());
            case BookAccess.PassphraseSource.StandardInput _ ->
                throw new AssertionError(
                    "These maintenance tests expect key-file-backed book access only.");
            case BookAccess.PassphraseSource.InteractivePrompt _ ->
                throw new AssertionError(
                    "These maintenance tests expect key-file-backed book access only.");
          };

  @Test
  void backupBook_createsClosedCopyBackupPairThatOpensWithTheMaterializedKeyFile()
      throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("entity.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(bookAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("entity.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("entity.key");

    BackupBookResult result =
        maintenanceService()
            .backupBook(bookAccess, backupFilePath, backupBookKeyFilePath)
            .requireAccepted();
    assertTrue(
        result instanceof BackupBookResult.BackedUp,
        () ->
            "Unexpected backup result: "
                + result
                + "; schema objects="
                + schemaObjects(bookAccess)
                + "; initialized semantics="
                + initializedSemantics(bookAccess));
    BackupBookResult.BackedUp backedUp = (BackupBookResult.BackedUp) result;

    assertEquals(hint(bookPath), backedUp.bookFilePath());
    assertEquals(hint(backupFilePath), backedUp.backupFilePath());
    assertEquals(hint(backupBookKeyFilePath), backedUp.backupBookKeyFilePath());
    assertTrue(Files.exists(backupFilePath));
    assertTrue(Files.exists(backupBookKeyFilePath));
    assertEquals(
        1,
        maintenanceMarkerCount(
            new BookAccess(
                backupFilePath, new BookAccess.PassphraseSource.KeyFile(backupBookKeyFilePath))));
    assertJournalLineCount(bookPath, 1);
    assertJournalContains(bookPath, "\"eventKind\":\"backup-created\"");
  }

  @Test
  void backupBook_rejectsMissingLiveBookAsArtifactVerificationFailure() {
    Path missingBookPath = tempDirectory.resolve("books").resolve("missing.sqlite");
    BookAccess bookAccess = bookAccess(missingBookPath);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("missing.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("missing.key");

    BackupBookResult.Rejected result =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            maintenanceService()
                .backupBook(bookAccess, backupFilePath, backupBookKeyFilePath)
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed rejection =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, result.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, rejection.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.MISSING, rejection.verificationFailure());
    assertEquals(hint(missingBookPath), rejection.artifactPath());
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
            maintenanceService()
                .backupBook(
                    bookAccess,
                    tempDirectory.resolve("backup").resolve("sidecar.sqlite"),
                    tempDirectory.resolve("backup").resolve("sidecar.key"))
                .requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts rejection =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, result.rejection());
    assertEquals(hint(bookPath), rejection.bookFilePath());
    assertTrue(
        rejection.blockingArtifactPaths().stream()
            .map(PublicPathHint::value)
            .anyMatch(value -> value.endsWith("/sidecar.sqlite-wal")));
  }

  @Test
  void backupBook_rejectsLiveBookPathsThatAreActivelyInUse() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("busy-live-book.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(bookAccess);

    try (SqliteNativeDatabase busyDatabase = openNativeDatabase(bookAccess)) {
      busyDatabase.executeStatement("begin immediate");

      BackupBookResult.Rejected result =
          assertInstanceOf(
              BackupBookResult.Rejected.class,
              maintenanceService()
                  .backupBook(
                      bookAccess,
                      tempDirectory.resolve("backup").resolve("busy-live-book.sqlite"),
                      tempDirectory.resolve("backup").resolve("busy-live-book.key"))
                  .requireAccepted());

      BookMaintenanceRejection.ArtifactBusy rejection =
          assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, result.rejection());
      assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, rejection.artifactRole());
      assertEquals(hint(bookPath), rejection.artifactPath());
      busyDatabase.executeStatement("rollback");
    }
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
            maintenanceService()
                .backupBook(
                    bookAccess,
                    existingBackupFilePath,
                    tempDirectory.resolve("backup").resolve("duplicate.key"))
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
            maintenanceService()
                .backupBook(bookAccess, backupFilePath, existingBackupKeyFilePath)
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
                maintenanceService().backupBook(bookAccess, backupFilePath, backupBookKeyFilePath));

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
    maintenanceService()
        .backupBook(liveBookAccess, backupFilePath, backupBookKeyFilePath)
        .requireAccepted();
    appendSampleRow(liveBookAccess, 2);
    assertEquals(2, maintenanceMarkerCount(liveBookAccess));

    RestoreBookResult result =
        maintenanceService()
            .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath)
            .requireAccepted();
    assertTrue(
        result instanceof RestoreBookResult.Restored, () -> "Unexpected restore result: " + result);
    RestoreBookResult.Restored restored = (RestoreBookResult.Restored) result;

    assertEquals(hint(bookPath), restored.bookFilePath());
    assertEquals(hint(backupFilePath), restored.backupFilePath());
    assertEquals(hint(backupBookKeyFilePath), restored.backupBookKeyFilePath());
    assertEquals(
        1,
        maintenanceMarkerCount(
            new BookAccess(
                bookPath, new BookAccess.PassphraseSource.KeyFile(backupBookKeyFilePath))));
    assertJournalLineCount(bookPath, 2);
    assertJournalContains(bookPath, "\"eventKind\":\"backup-restored\"");
  }

  @Test
  void restoreBook_rejectsBlockingArtifactsOnLiveBookAndBackupSource() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("restore-blocked.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("restore-blocked.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("restore-blocked.key");
    maintenanceService()
        .backupBook(liveBookAccess, backupFilePath, backupBookKeyFilePath)
        .requireAccepted();

    Files.writeString(bookPath.resolveSibling("restore-blocked.sqlite-wal"), "live sidecar");
    RestoreBookResult.Rejected liveBlocked =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            maintenanceService()
                .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.BookHasBlockingArtifacts.class, liveBlocked.rejection());
    Files.deleteIfExists(bookPath.resolveSibling("restore-blocked.sqlite-wal"));

    Files.createDirectories(backupFilePath.getParent());
    Files.writeString(
        backupFilePath.resolveSibling("restore-blocked.sqlite-wal"), "backup sidecar");
    RestoreBookResult.Rejected backupBlocked =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            maintenanceService()
                .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.BackupSourceHasBlockingArtifacts.class, backupBlocked.rejection());
  }

  @Test
  void restoreBook_rejectsMissingBackupSourceAsArtifactVerificationFailure() {
    Path bookPath = tempDirectory.resolve("books").resolve("restore-missing.sqlite");
    Path missingBackupPath = tempDirectory.resolve("backup").resolve("missing.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("missing.key");

    RestoreBookResult.Rejected result =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            maintenanceService()
                .restoreBook(bookPath, missingBackupPath, backupBookKeyFilePath)
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed rejection =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, result.rejection());
    assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, rejection.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.MISSING, rejection.verificationFailure());
    assertEquals(hint(missingBackupPath), rejection.artifactPath());
  }

  @Test
  void restoreBook_rejectsBusyLiveBookAndBusyBackupSource() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("restore-busy.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("restore-busy.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("restore-busy.key");
    maintenanceService()
        .backupBook(liveBookAccess, backupFilePath, backupBookKeyFilePath)
        .requireAccepted();
    BookAccess backupAccess =
        new BookAccess(
            backupFilePath, new BookAccess.PassphraseSource.KeyFile(backupBookKeyFilePath));

    try (SqliteNativeDatabase busyLiveBook = openNativeDatabase(liveBookAccess)) {
      busyLiveBook.executeStatement("begin immediate");
      RestoreBookResult.Rejected busyLiveResult =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              maintenanceService()
                  .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath)
                  .requireAccepted());
      BookMaintenanceRejection.ArtifactBusy busyLiveRejection =
          assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, busyLiveResult.rejection());
      assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busyLiveRejection.artifactRole());
      assertEquals(hint(bookPath), busyLiveRejection.artifactPath());
      busyLiveBook.executeStatement("rollback");
    }

    try (SqliteNativeDatabase busyBackupSource = openNativeDatabase(backupAccess)) {
      busyBackupSource.executeStatement("begin immediate");
      RestoreBookResult.Rejected busyBackupResult =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              maintenanceService()
                  .restoreBook(bookPath, backupFilePath, backupBookKeyFilePath)
                  .requireAccepted());
      BookMaintenanceRejection.ArtifactBusy busyBackupRejection =
          assertInstanceOf(
              BookMaintenanceRejection.ArtifactBusy.class, busyBackupResult.rejection());
      assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, busyBackupRejection.artifactRole());
      assertEquals(hint(backupFilePath), busyBackupRejection.artifactPath());
      busyBackupSource.executeStatement("rollback");
    }
  }

  @Test
  void recoverRekey_inspectsAndRestoresTheSingleSiblingRollbackArtifact() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("recover.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());
    appendSampleRow(liveBookAccess, 2);
    assertEquals(2, maintenanceMarkerCount(liveBookAccess));

    RekeyRollbackResult.Inspected inspected =
        assertInstanceOf(
            RekeyRollbackResult.Inspected.class,
            maintenanceService().inspectRekeyRollback(bookPath).requireAccepted());
    assertEquals(List.of(hint(rollbackFile.path())), inspected.rollbackArtifactPaths());

    RekeyRollbackResult restoredResult =
        maintenanceService()
            .restoreRekeyRollback(bookPath, null, liveBookAccess.passphraseSource())
            .requireAccepted();
    assertTrue(
        restoredResult instanceof RekeyRollbackResult.Restored,
        () -> "Unexpected restore-rekey-rollback result: " + restoredResult);
    RekeyRollbackResult.Restored restored = (RekeyRollbackResult.Restored) restoredResult;
    assertEquals(hint(rollbackFile.path()), restored.rollbackArtifactPath());
    assertEquals(1, maintenanceMarkerCount(liveBookAccess));
    assertJournalLineCount(bookPath, 2);
    assertJournalContains(bookPath, "\"eventKind\":\"rekey-rollback-inspected\"");
    assertJournalContains(bookPath, "\"eventKind\":\"rekey-rollback-restored\"");
  }

  @Test
  void recoverRekey_deleteRemovesTheSelectedRollbackArtifact() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("delete-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());

    RekeyRollbackResult.Deleted deleted =
        assertInstanceOf(
            RekeyRollbackResult.Deleted.class,
            maintenanceService()
                .deleteRekeyRollback(bookPath, rollbackFile.path())
                .requireAccepted());

    assertEquals(hint(rollbackFile.path()), deleted.rollbackArtifactPath());
    assertFalse(Files.exists(rollbackFile.path()));
    assertJournalLineCount(bookPath, 1);
    assertJournalContains(bookPath, "\"eventKind\":\"rekey-rollback-deleted\"");
  }

  @Test
  void recoverRekey_rejectsWhenNoRollbackArtifactsExist() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("missing-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            maintenanceService()
                .restoreRekeyRollback(bookPath, null, liveBookAccess.passphraseSource())
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

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            maintenanceService()
                .restoreRekeyRollback(bookPath, null, liveBookAccess.passphraseSource())
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

    RekeyRollbackResult.Rejected missingRejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            maintenanceService()
                .deleteRekeyRollback(bookPath, missingRollbackArtifact)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactNotFound.class, missingRejected.rejection());

    Path otherBookPath = tempDirectory.resolve("books").resolve("other.sqlite");
    BookAccess otherBookAccess = bookAccess(otherBookPath);
    initializeBookWithSampleRow(otherBookAccess);
    SqliteRekeyRollbackFile otherRollbackArtifact =
        SqliteRekeyRollbackFile.create(otherBookPath.toAbsolutePath().normalize());

    RekeyRollbackResult.Rejected mismatchRejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            maintenanceService()
                .restoreRekeyRollback(
                    bookPath, otherRollbackArtifact.path(), liveBookAccess.passphraseSource())
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

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            maintenanceService()
                .restoreRekeyRollback(
                    bookPath, rollbackFile.path(), liveBookAccess.passphraseSource())
                .requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(
        Set.of(hint(bookPath.resolveSibling("blocked-restore.sqlite-wal"))),
        Set.copyOf(blockingArtifacts.blockingArtifactPaths()));
  }

  @Test
  void recoverRekey_restoreRejectsUnverifiedRollbackArtifactContents() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("invalid-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    Path invalidRollbackArtifact =
        bookPath
            .resolveSibling(bookPath.getFileName() + ".rekey-rollback-invalid.sqlite")
            .toAbsolutePath()
            .normalize();
    Files.writeString(invalidRollbackArtifact, "not a protected book");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            maintenanceService()
                .restoreRekeyRollback(
                    bookPath, invalidRollbackArtifact, liveBookAccess.passphraseSource())
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, verificationFailed.artifactRole());
    assertEquals(hint(invalidRollbackArtifact), verificationFailed.artifactPath());
  }

  @Test
  void recoverRekey_restoreRejectsBusyRollbackArtifact() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("busy-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());
    BookAccess rollbackAccess =
        new BookAccess(rollbackFile.path(), liveBookAccess.passphraseSource());

    try (SqliteNativeDatabase busyRollbackArtifact = openNativeDatabase(rollbackAccess)) {
      busyRollbackArtifact.executeStatement("begin immediate");

      RekeyRollbackResult.Rejected rejected =
          assertInstanceOf(
              RekeyRollbackResult.Rejected.class,
              maintenanceService()
                  .restoreRekeyRollback(
                      bookPath, rollbackFile.path(), liveBookAccess.passphraseSource())
                  .requireAccepted());

      BookMaintenanceRejection.ArtifactBusy busyRejection =
          assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
      assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, busyRejection.artifactRole());
      assertEquals(hint(rollbackFile.path()), busyRejection.artifactPath());
      busyRollbackArtifact.executeStatement("rollback");
    }
  }

  @Test
  void backupBook_rejectsInitializedBooksWithUnexpectedSchemaObjects() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("unexpected-schema.sqlite");
    BookAccess bookAccess = bookAccess(bookPath);
    initializeBookWithSampleRow(bookAccess);
    withOpenDatabase(
        bookAccess,
        database ->
            database.executeStatement("create table unexpected_schema (id integer primary key)"));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            maintenanceService()
                .backupBook(
                    bookAccess,
                    tempDirectory.resolve("backup").resolve("unexpected-schema.sqlite"),
                    tempDirectory.resolve("backup").resolve("unexpected-schema.key"))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(
        BookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND,
        verificationFailed.verificationFailure());
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
              () -> maintenanceService().inspectRekeyRollback(bookPath));
      assertTrue(NullTestSupport.messageOf(exception).contains("rollback artifacts"));
      assertEquals("scan-boom", NullTestSupport.messageOf(NullTestSupport.causeOf(exception)));
    }
  }

  private ProtectedBookMaintenanceService maintenanceService() {
    return new ProtectedBookMaintenanceService(
        Clock.systemUTC(), new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER));
  }

  private static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
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
          SqliteAuditEventWriter.insertAuditEvent(
              database,
              dev.erst.fingrind.executor.bookkeeping.BookAuditEvent.bookOpened(
                  Instant.parse("2026-04-07T10:15:30Z")));
        });
  }

  private void appendSampleRow(BookAccess bookAccess, int id) {
    withOpenDatabase(
        bookAccess,
        database ->
            SqliteAuditEventWriter.insertAuditEvent(
                database,
                dev.erst.fingrind.executor.bookkeeping.BookAuditEvent.bookOpened(
                    Instant.parse("2026-04-07T10:15:%02dZ".formatted(30 + id)))));
  }

  private int maintenanceMarkerCount(BookAccess bookAccess) {
    final int[] count = new int[1];
    withOpenDatabase(
        bookAccess,
        database -> {
          try (SqliteNativeStatement statement =
              SqliteNativeStatements.prepare(
                  database,
                  """
                  select count(*)
                  from audit_event
                  where event_kind = 'BOOK_OPENED'
                  """)) {
            assertEquals(SqliteNativeResultCodes.ROW, statement.step());
            count[0] = statement.columnInt(0);
            assertEquals(SqliteNativeResultCodes.DONE, statement.step());
          }
        });
    return count[0];
  }

  private void assertJournalLineCount(Path bookPath, int expectedCount) throws IOException {
    assertEquals(expectedCount, Files.readAllLines(journalPath(bookPath)).size());
  }

  private void assertJournalContains(Path bookPath, String expectedFragment) throws IOException {
    assertTrue(
        Files.readString(journalPath(bookPath)).contains(expectedFragment),
        () -> "Expected maintenance journal to contain: " + expectedFragment);
  }

  private static Path journalPath(Path bookPath) {
    return SqliteProtectedBookMaintenanceJournal.journalPath(bookPath);
  }

  private List<String> schemaObjects(BookAccess bookAccess) {
    List<String> objects = new java.util.ArrayList<>();
    withOpenDatabase(
        bookAccess,
        database -> {
          try (SqliteNativeStatement statement =
              SqliteNativeStatements.prepare(
                  database,
                  """
                  select type || ':' || name
                  from sqlite_schema
                  where type in ('table', 'index', 'trigger', 'view')
                    and name not like 'sqlite_%'
                  order by type, name
                  """)) {
            while (statement.step() == SqliteNativeResultCodes.ROW) {
              objects.add(statement.columnText(0));
            }
          }
        });
    return List.copyOf(objects);
  }

  private String initializedSemantics(BookAccess bookAccess) {
    StringBuilder builder = new StringBuilder(192);
    withOpenDatabase(
        bookAccess,
        database ->
            builder
                .append("noUnexpectedSchema=")
                .append(SqliteBookIntegrityVerifier.hasNoUnexpectedSchemaObjects(database))
                .append(", integrity=")
                .append(SqliteBookIntegrityVerifier.passesIntegrityCheck(database))
                .append(", foreignKeys=")
                .append(SqliteBookIntegrityVerifier.passesForeignKeyCheck(database))
                .append(", schemaFingerprint=")
                .append(SqliteBookIntegrityVerifier.hasMatchingRecordedSchemaFingerprint(database))
                .append(", balancedJournal=")
                .append(SqliteBookIntegrityVerifier.hasBalancedPersistedJournal(database))
                .append(", bookIdentity=")
                .append(SqliteStatementQueries.loadBookIdentity(database).isPresent())
                .append(", validMoney=")
                .append(SqliteBookIntegrityVerifier.hasValidPersistedMoney(database))
                .append(", functionalCurrencyAligned=")
                .append(SqliteBookIntegrityVerifier.hasFunctionalCurrencyAlignedJournal(database)));
    return builder.toString();
  }
}
