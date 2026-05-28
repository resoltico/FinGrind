package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioral tests for the executor-owned protected-book maintenance service on SQLite. */
class SqliteProtectedBookMaintenanceServiceTest extends SqliteNativeBridgeTestSupport {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-19T12:00:00Z"), java.time.ZoneOffset.UTC);
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
  void backupBook_createsOneVerifiedBackupPairAndRecordsOneInBookAuditWithoutPlaintextJournal() {
    Path bookPath = tempDirectory.resolve("books").resolve("entity.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBook(liveBookAccess);
    Path backupFilePath = tempDirectory.resolve("backup").resolve("entity.sqlite");
    Path backupBookKeyFilePath = tempDirectory.resolve("backup").resolve("entity.key");

    BackupBookResult.BackedUp backedUp =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            maintenanceService()
                .backupBook(liveBookAccess, backupFilePath, backupBookKeyFilePath)
                .requireAccepted());

    assertEquals(hint(bookPath), backedUp.bookFilePath());
    assertEquals(hint(backupFilePath), backedUp.backupFilePath());
    assertEquals(hint(backupBookKeyFilePath), backedUp.backupBookKeyFilePath());
    assertTrue(Files.exists(backupFilePath));
    assertTrue(Files.exists(backupBookKeyFilePath));
    assertEquals(1, auditEventCount(liveBookAccess, "BACKUP_CREATED"));
    assertEquals(
        0,
        auditEventCount(
            new BookAccess(
                backupFilePath, new BookAccess.PassphraseSource.KeyFile(backupBookKeyFilePath)),
            "BACKUP_CREATED"));
    assertFalse(Files.exists(maintenanceJournalPath(bookPath)));
    assertFalse(Files.exists(maintenanceJournalPath(backupFilePath)));
  }

  @Test
  void backupBook_rejectsOneMissingLiveBookAsOneVerificationFailure() {
    Path missingBookPath = tempDirectory.resolve("books").resolve("missing.sqlite");
    BookAccess missingBookAccess = bookAccess(missingBookPath);

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            maintenanceService()
                .backupBook(
                    missingBookAccess,
                    tempDirectory.resolve("backup").resolve("missing.sqlite"),
                    tempDirectory.resolve("backup").resolve("missing.key"))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failure =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, failure.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.MISSING, failure.verificationFailure());
  }

  @Test
  void restoreBook_rejectsOneBackupSourceThatMatchesTheSelectedLiveBookPath() {
    Path bookPath = tempDirectory.resolve("books").resolve("same-path.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBook(liveBookAccess);
    Path backupBookKeyFilePath = keyFilePath(liveBookAccess);

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            maintenanceService()
                .restoreBook(bookPath, bookPath, backupBookKeyFilePath)
                .requireAccepted());

    BookMaintenanceRejection.BackupSourceMatchesLiveBook conflict =
        assertInstanceOf(
            BookMaintenanceRejection.BackupSourceMatchesLiveBook.class, rejected.rejection());
    assertEquals(hint(bookPath), conflict.bookFilePath());
    assertEquals(hint(bookPath), conflict.backupFilePath());
    assertEquals(0, auditEventCount(liveBookAccess, "BACKUP_RESTORED"));
  }

  @Test
  void inspectRekeyRollback_isSideEffectFree() {
    Path bookPath = tempDirectory.resolve("books").resolve("recover.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBook(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());

    RekeyRollbackResult.Inspected inspected =
        assertInstanceOf(
            RekeyRollbackResult.Inspected.class,
            maintenanceService().inspectRekeyRollback(bookPath).requireAccepted());

    assertEquals(List.of(hint(rollbackFile.path())), inspected.rollbackArtifactPaths());
    assertEquals(0, auditEventCount(liveBookAccess, "REKEY_ROLLBACK_RESTORED"));
    assertEquals(0, auditEventCount(liveBookAccess, "REKEY_ROLLBACK_DELETED"));
    assertFalse(Files.exists(maintenanceJournalPath(bookPath)));
  }

  @Test
  void deleteRekeyRollback_removesOneSelectedRollbackArtifactAndRecordsOneInBookAudit() {
    Path bookPath = tempDirectory.resolve("books").resolve("delete-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBook(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());

    RekeyRollbackResult.Deleted deleted =
        assertInstanceOf(
            RekeyRollbackResult.Deleted.class,
            maintenanceService()
                .deleteRekeyRollback(liveBookAccess, rollbackFile.path())
                .requireAccepted());

    assertEquals(hint(rollbackFile.path()), deleted.rollbackArtifactPath());
    assertFalse(Files.exists(rollbackFile.path()));
    assertEquals(1, auditEventCount(liveBookAccess, "REKEY_ROLLBACK_DELETED"));
    assertFalse(Files.exists(maintenanceJournalPath(bookPath)));
  }

  @Test
  void restoreRekeyRollback_rejectsOneInvalidRollbackArtifact() throws Exception {
    Path bookPath = tempDirectory.resolve("books").resolve("invalid-rollback.sqlite");
    BookAccess liveBookAccess = bookAccess(bookPath);
    initializeBook(liveBookAccess);
    SqliteRekeyRollbackFile rollbackFile =
        SqliteRekeyRollbackFile.create(bookPath.toAbsolutePath().normalize());
    Files.deleteIfExists(rollbackFile.path());
    SqliteStoreFixtureSupport.createEmptySqliteFile(rollbackFile.path());

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            maintenanceService()
                .restoreRekeyRollback(
                    bookPath, rollbackFile.path(), liveBookAccess.passphraseSource())
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failure =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, failure.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.BLANK_SQLITE, failure.verificationFailure());
    assertEquals(0, auditEventCount(liveBookAccess, "REKEY_ROLLBACK_RESTORED"));
  }

  private ProtectedBookMaintenanceService maintenanceService() {
    return new ProtectedBookMaintenanceService(
        FIXED_CLOCK, new SqliteProtectedBookMaintenanceStore(KEY_FILE_RESOLVER));
  }

  private static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  private void initializeBook(BookAccess bookAccess) {
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
              database, BookAuditEvent.bookOpened(Instant.parse("2026-05-19T08:00:00Z")));
        });
  }

  private int auditEventCount(BookAccess bookAccess, String eventKind) {
    final int[] count = new int[1];
    withOpenDatabase(
        bookAccess,
        database -> {
          try (SqliteNativeStatement statement =
              SqliteNativeStatements.prepare(
                  database,
                  "select count(*) from audit_event where event_kind = '" + eventKind + "'")) {
            assertEquals(SqliteNativeResultCodes.ROW, statement.step());
            count[0] = statement.columnInt(0);
            assertEquals(SqliteNativeResultCodes.DONE, statement.step());
          }
        });
    return count[0];
  }

  private static Path maintenanceJournalPath(Path bookPath) {
    return bookPath.resolveSibling(bookPath.getFileName().toString() + ".maintenance-log.jsonl");
  }

  private static Path keyFilePath(BookAccess bookAccess) {
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput _ ->
          throw new AssertionError("Expected one key-file-backed access tuple.");
      case BookAccess.PassphraseSource.InteractivePrompt _ ->
          throw new AssertionError("Expected one key-file-backed access tuple.");
    };
  }
}
