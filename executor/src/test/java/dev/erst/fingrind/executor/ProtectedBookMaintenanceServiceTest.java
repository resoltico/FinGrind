package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.access;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.hint;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.internalError;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.path;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.service;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.touch;
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
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link ProtectedBookMaintenanceService}. */
class ProtectedBookMaintenanceServiceTest {
  @TempDir Path tempDirectory;

  @Test
  void backupBook_rejectsOneInvalidStagedBackupArtifactWithoutRecordingAudit() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.verifications.put(
        store.normalized(backup),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(backup), ProtectedBookVerificationFailure.FOREIGN_SQLITE)));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, failed.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.FOREIGN_SQLITE, failed.verificationFailure());
    assertTrue(store.recordedAudits.isEmpty());
    assertFalse(store.lastStagedBackupPairCommitted);
    assertTrue(store.lastStagedBackupPairClosed);
  }

  @Test
  void backupBook_publishesOneVerifiedBackupPairWithoutMutatingTheLiveBookAudit() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");

    BackupBookResult.BackedUp backedUp =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    assertEquals(hint(book), backedUp.bookFilePath());
    assertEquals(hint(backup), backedUp.backupFilePath());
    assertEquals(hint(backupKey), backedUp.backupBookKeyFilePath());
    assertTrue(store.recordedAudits.isEmpty());
    assertTrue(store.lastStagedBackupPairCommitted);
    assertTrue(store.lastStagedBackupPairClosed);
  }

  @Test
  void backupBook_rejectsOneLiveBookWithBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path wal = path(tempDirectory, "book.sqlite-wal");
    store.bookBlockingArtifacts.put(store.normalized(book), List.of(store.normalized(wal)));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(wal)), blockingArtifacts.blockingArtifactPaths());
    assertTrue(store.recordedAudits.isEmpty());
  }

  @Test
  void backupBook_rejectsOneExistingBackupDestination() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    touch(backup);

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.BackupDestinationAlreadyExists.class, rejected.rejection());
  }

  @Test
  void backupBook_rejectsOneExistingBackupKeyDestination() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    touch(backupKey);

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.SecretTargetOccupied.class, rejected.rejection());
  }

  @Test
  void backupBook_rejectsOneBusyLiveBookLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.busyManagedArtifacts.add(store.normalized(book));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
  }

  @Test
  void backupBook_rejectsOneInvalidLiveBookBeforeBackupStaging() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.verifications.put(
        store.normalized(book),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(book), ProtectedBookVerificationFailure.MISSING)));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, failed.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.MISSING, failed.verificationFailure());
  }

  @Test
  void restoreBook_rejectsOneBackupSourceThatMatchesTheSelectedLiveBookPath() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backupKey = path(tempDirectory, "backup.book-key");

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, book, backupKey, false).requireAccepted());

    BookMaintenanceRejection.BackupSourceMatchesLiveBook conflict =
        assertInstanceOf(
            BookMaintenanceRejection.BackupSourceMatchesLiveBook.class, rejected.rejection());
    assertEquals(hint(book), conflict.bookFilePath());
    assertEquals(hint(book), conflict.backupFilePath());
    assertTrue(store.recordedAudits.isEmpty());
  }

  @Test
  void restoreBook_restoresOneVerifiedBackupAndRecordsOneInBookAudit() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path staged = path(tempDirectory, "staged-restore.sqlite");
    touch(backup);
    touch(backupKey);
    store.stagedReplacementPath = staged;

    RestoreBookResult.Restored restored =
        assertInstanceOf(
            RestoreBookResult.Restored.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(hint(bookKey), restored.bookKeyFilePath());
    assertEquals(1, store.recordedAudits.size());
    RecordedAudit audit = store.recordedAudits.getFirst();
    assertEquals(store.normalized(staged), audit.bookPath());
    assertEquals(FIXED_CLOCK.instant(), audit.recordedAt());
    assertEquals(ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED, audit.auditKind());
  }

  @Test
  void restoreBook_rejectsOneLiveBookWithBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path wal = path(tempDirectory, "book.sqlite-wal");
    store.bookBlockingArtifacts.put(store.normalized(book), List.of(store.normalized(wal)));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(wal)), blockingArtifacts.blockingArtifactPaths());
  }

  @Test
  void restoreBook_rejectsOneBackupSourceWithBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path wal = path(tempDirectory, "backup.sqlite-wal");
    store.backupBlockingArtifacts.put(store.normalized(backup), List.of(store.normalized(wal)));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BackupSourceHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(wal)), blockingArtifacts.blockingArtifactPaths());
  }

  @Test
  void restoreBook_rejectsOneInvalidBackupSourceBeforeLeasing() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.verifications.put(
        store.normalized(backup),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(backup), ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND)));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, failed.artifactRole());
    assertEquals(
        BookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND, failed.verificationFailure());
  }

  @Test
  void restoreBook_rejectsOneBusyLiveBookLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.busyManagedArtifacts.add(store.normalized(book));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
  }

  @Test
  void restoreBook_rejectsOneBusyBackupSourceLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.busyExistingArtifacts.add(store.normalized(backup));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, busy.artifactRole());
  }

  @Test
  void restoreBook_rejectsOneInvalidStagedReplacementTarget() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path staged = path(tempDirectory, "staged-restore.sqlite");
    store.stagedReplacementPath = staged;
    store.verifications.put(
        store.normalized(staged),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(staged),
                ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED)));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.RESTORED_TARGET, failed.artifactRole());
    assertEquals(
        BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
        failed.verificationFailure());
  }

  @Test
  void restoreBook_projectsOneStoreFailureWithoutWrappingItAgain() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = restoreBookKey(book);
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    ContractFailure failure = internalError("backupFilePath");
    store.appendAuditFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure));

    assertEquals(
        failure,
        service(store).restoreBook(book, bookKey, backup, backupKey, false).requireRejected());
  }

  private static Path restoreBookKey(Path bookFilePath) {
    return bookFilePath
        .resolveSibling(bookFilePath.getFileName().toString() + ".book-key")
        .toAbsolutePath()
        .normalize();
  }

  @Test
  void inspectRekeyRollback_isSideEffectFree() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));

    RekeyRollbackResult.Inspected inspected =
        assertInstanceOf(
            RekeyRollbackResult.Inspected.class,
            service(store).inspectRekeyRollback(book).requireAccepted());

    assertEquals(hint(book), inspected.bookFilePath());
    assertEquals(List.of(hint(rollback)), inspected.rollbackArtifactPaths());
    assertTrue(store.recordedAudits.isEmpty());
  }

  @Test
  void deleteRekeyRollback_requiresVerifiedBookAccessAndRecordsOneInBookAudit() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(book);
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));

    RekeyRollbackResult.Deleted deleted =
        assertInstanceOf(
            RekeyRollbackResult.Deleted.class,
            service(store).deleteRekeyRollback(access(book), rollback).requireAccepted());

    assertEquals(hint(book), deleted.bookFilePath());
    assertEquals(hint(rollback), deleted.rollbackArtifactPath());
    assertEquals(1, store.recordedAudits.size());
    RecordedAudit audit = store.recordedAudits.getFirst();
    assertEquals(store.normalized(book), audit.bookPath());
    assertEquals(FIXED_CLOCK.instant(), audit.recordedAt());
    assertEquals(ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_DELETED, audit.auditKind());
    assertTrue(store.lastRollbackDeletionCommitted);
    assertTrue(store.lastRollbackDeletionClosed);
  }

  @Test
  void deleteRekeyRollback_requiresOneRollbackArtifactToExist() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), null).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.NoRollbackArtifactsFound.class, rejected.rejection());
  }

  @Test
  void deleteRekeyRollback_requiresOneExplicitSelectionWhenManyRollbackArtifactsExist() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollbackOne = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    Path rollbackTwo = path(tempDirectory, "book.rekey-rollback-2.sqlite");
    store.rollbackArtifacts.put(
        store.normalized(book),
        List.of(store.normalized(rollbackOne), store.normalized(rollbackTwo)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), null).requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactSelectionRequired.class, rejected.rejection());
  }

  @Test
  void deleteRekeyRollback_rejectsOneMissingExplicitRollbackArtifact() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path missingRollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), missingRollback).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.RollbackArtifactNotFound.class, rejected.rejection());
  }

  @Test
  void deleteRekeyRollback_rejectsOneRollbackArtifactThatDoesNotBelongToTheBook() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path foreignRollback = path(tempDirectory, "foreign.rekey-rollback-1.sqlite");
    touch(foreignRollback);

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), foreignRollback).requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactNotForBook.class, rejected.rejection());
  }

  @Test
  void deleteRekeyRollback_rejectsOneInvalidLiveBookBeforeDeletingRollbackArtifact() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.verifications.put(
        store.normalized(book),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(book), ProtectedBookVerificationFailure.BLANK_SQLITE)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), rollback).requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, failed.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.BLANK_SQLITE, failed.verificationFailure());
  }

  @Test
  void deleteRekeyRollback_rejectsOneBusyLiveBookLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.busyExistingArtifacts.add(store.normalized(book));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), rollback).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
  }

  @Test
  void deleteRekeyRollback_rejectsOneBusyRollbackArtifactLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.busyExistingArtifacts.add(store.normalized(rollback));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), rollback).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, busy.artifactRole());
  }

  @Test
  void restoreRekeyRollback_rejectsOneInvalidRollbackArtifact() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.verifications.put(
        store.normalized(rollback),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(rollback), ProtectedBookVerificationFailure.BLANK_SQLITE)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    rollback,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, failed.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.BLANK_SQLITE, failed.verificationFailure());
    assertTrue(store.recordedAudits.isEmpty());
  }

  @Test
  void restoreRekeyRollback_requiresOneRollbackArtifactToExistWhenNoSelectionIsProvided() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    null,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.NoRollbackArtifactsFound.class, rejected.rejection());
  }

  @Test
  void restoreRekeyRollback_requiresOneExplicitSelectionWhenManyRollbackArtifactsExist() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollbackOne = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    Path rollbackTwo = path(tempDirectory, "book.rekey-rollback-2.sqlite");
    touch(rollbackOne);
    touch(rollbackTwo);
    store.rollbackArtifacts.put(
        store.normalized(book),
        List.of(store.normalized(rollbackOne), store.normalized(rollbackTwo)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    null,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactSelectionRequired.class, rejected.rejection());
  }

  @Test
  void restoreRekeyRollback_rejectsOneLiveBookWithNonRollbackBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    Path wal = path(tempDirectory, "book.sqlite-wal");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.bookBlockingArtifacts.put(
        store.normalized(book), List.of(store.normalized(rollback), store.normalized(wal)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    rollback,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(wal)), blockingArtifacts.blockingArtifactPaths());
  }

  @Test
  void restoreRekeyRollback_rejectsOneBusyLiveBookLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.busyManagedArtifacts.add(store.normalized(book));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    rollback,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
  }

  @Test
  void restoreRekeyRollback_rejectsOneBusyRollbackArtifactLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.busyExistingArtifacts.add(store.normalized(rollback));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    rollback,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, busy.artifactRole());
  }

  @Test
  void restoreRekeyRollback_rejectsOneInvalidRestoredTargetVerification() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    Path staged = path(tempDirectory, "staged-rollback.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.stagedReplacementPath = staged;
    store.verifications.put(
        store.normalized(staged),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(staged),
                ProtectedBookVerificationFailure.UNSUPPORTED_FORMAT_VERSION)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    rollback,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.RESTORED_TARGET, failed.artifactRole());
    assertEquals(
        BookMaintenanceVerificationFailure.UNSUPPORTED_FORMAT_VERSION,
        failed.verificationFailure());
  }

  @Test
  void restoreRekeyRollback_restoresOneImplicitlySelectedVerifiedRollbackArtifact() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    Path staged = path(tempDirectory, "staged-rollback.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.stagedReplacementPath = staged;

    RekeyRollbackResult.Restored restored =
        assertInstanceOf(
            RekeyRollbackResult.Restored.class,
            service(store)
                .restoreRekeyRollback(
                    book,
                    null,
                    new BookAccess.PassphraseSource.KeyFile(path(tempDirectory, "book.key")))
                .requireAccepted());

    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(hint(rollback), restored.rollbackArtifactPath());
    assertEquals(1, store.recordedAudits.size());
    RecordedAudit audit = store.recordedAudits.getFirst();
    assertEquals(store.normalized(staged), audit.bookPath());
    assertEquals(FIXED_CLOCK.instant(), audit.recordedAt());
    assertEquals(ProtectedBookMaintenanceAuditKind.REKEY_ROLLBACK_RESTORED, audit.auditKind());
  }

  @Test
  void service_projectsOneStoreFailureWithoutWrappingItAgain() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    ContractFailure failure = internalError("backupFilePath");
    store.stageBackupFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure));

    assertEquals(
        failure, service(store).backupBook(access(book), backup, backupKey).requireRejected());
  }

  @Test
  void backupBook_publicationFailureDoesNotMutateTheLiveBookAudit() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.failBackupPairCommit = true;

    ContractFailure failure =
        service(store).backupBook(access(book), backup, backupKey).requireRejected();

    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.descriptor());
    assertTrue(store.recordedAudits.isEmpty());
    assertTrue(store.compensatedAudits.isEmpty());
  }

  @Test
  void backupBook_publicationFailureDoesNotCallAuditCompensation() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    ContractFailure compensateFailure = internalError("bookFilePath");
    store.failBackupPairCommit = true;
    store.compensateAuditFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(compensateFailure));

    ContractFailure failure =
        service(store).backupBook(access(book), backup, backupKey).requireRejected();

    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.descriptor());
    assertTrue(store.recordedAudits.isEmpty());
    assertTrue(store.compensatedAudits.isEmpty());
  }

  @Test
  void deleteRekeyRollback_appendsOneCompensatingAuditWhenDeletionCommitFails() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path rollback = path(tempDirectory, "book.rekey-rollback-1.sqlite");
    touch(book);
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.failRollbackDeletionCommit = true;

    ContractFailure failure =
        service(store).deleteRekeyRollback(access(book), rollback).requireRejected();

    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.descriptor());
    assertEquals(1, store.recordedAudits.size());
    assertEquals(1, store.compensatedAudits.size());
  }
}
