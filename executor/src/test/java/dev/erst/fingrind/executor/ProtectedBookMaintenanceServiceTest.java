package dev.erst.fingrind.executor;

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
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.maintenance.MaintenanceCompletion;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditCompensationKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceAuditKind;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link ProtectedBookMaintenanceService}. */
class ProtectedBookMaintenanceServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-19T10:15:30Z"), ZoneOffset.UTC);

  @TempDir Path tempDirectory;

  @Test
  void backupBook_rejectsOneInvalidStagedBackupArtifactWithoutRecordingAudit() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
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
  void backupBook_recordsOneDeterministicAuditBeforePublishingOneVerifiedBackupPair() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");

    BackupBookResult.BackedUp backedUp =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    assertEquals(hint(book), backedUp.bookFilePath());
    assertEquals(hint(backup), backedUp.backupFilePath());
    assertEquals(hint(backupKey), backedUp.backupBookKeyFilePath());
    assertEquals(1, store.recordedAudits.size());
    RecordedAudit audit = store.recordedAudits.getFirst();
    assertEquals(store.normalized(book), audit.bookPath());
    assertEquals(FIXED_CLOCK.instant(), audit.recordedAt());
    assertEquals(ProtectedBookMaintenanceAuditKind.BACKUP_CREATED, audit.auditKind());
    assertTrue(store.lastStagedBackupPairCommitted);
    assertTrue(store.lastStagedBackupPairClosed);
  }

  @Test
  void backupBook_rejectsOneLiveBookWithBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    Path wal = path("book.sqlite-wal");
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
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
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
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    touch(backupKey);

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.BackupKeyFileAlreadyExists.class, rejected.rejection());
  }

  @Test
  void backupBook_rejectsOneBusyLiveBookLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
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
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
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
    Path book = path("book.sqlite");
    Path backupKey = path("backup.book-key");

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, book, backupKey).requireAccepted());

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
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    Path staged = path("staged-restore.sqlite");
    touch(backup);
    touch(backupKey);
    store.stagedReplacementPath = staged;

    RestoreBookResult.Restored restored =
        assertInstanceOf(
            RestoreBookResult.Restored.class,
            service(store).restoreBook(book, backup, backupKey).requireAccepted());

    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(hint(backup), restored.backupFilePath());
    assertEquals(hint(backupKey), restored.backupBookKeyFilePath());
    assertEquals(1, store.recordedAudits.size());
    RecordedAudit audit = store.recordedAudits.getFirst();
    assertEquals(store.normalized(staged), audit.bookPath());
    assertEquals(FIXED_CLOCK.instant(), audit.recordedAt());
    assertEquals(ProtectedBookMaintenanceAuditKind.BACKUP_RESTORED, audit.auditKind());
  }

  @Test
  void restoreBook_rejectsOneLiveBookWithBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    Path wal = path("book.sqlite-wal");
    store.bookBlockingArtifacts.put(store.normalized(book), List.of(store.normalized(wal)));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, backup, backupKey).requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(wal)), blockingArtifacts.blockingArtifactPaths());
  }

  @Test
  void restoreBook_rejectsOneBackupSourceWithBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    Path wal = path("backup.sqlite-wal");
    store.backupBlockingArtifacts.put(store.normalized(backup), List.of(store.normalized(wal)));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, backup, backupKey).requireAccepted());

    BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BackupSourceHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(wal)), blockingArtifacts.blockingArtifactPaths());
  }

  @Test
  void restoreBook_rejectsOneInvalidBackupSourceBeforeLeasing() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    store.verifications.put(
        store.normalized(backup),
        MaintenanceDecision.accepted(
            new ProtectedBookMaintenanceStore.VerificationFailure(
                store.normalized(backup), ProtectedBookVerificationFailure.INCOMPLETE_FINGRIND)));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, backup, backupKey).requireAccepted());

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
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    store.busyManagedArtifacts.add(store.normalized(book));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, backup, backupKey).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
  }

  @Test
  void restoreBook_rejectsOneBusyBackupSourceLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    store.busyExistingArtifacts.add(store.normalized(backup));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, backup, backupKey).requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, busy.artifactRole());
  }

  @Test
  void restoreBook_rejectsOneInvalidStagedReplacementTarget() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    Path staged = path("staged-restore.sqlite");
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
            service(store).restoreBook(book, backup, backupKey).requireAccepted());

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
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    ContractFailure failure = runtimeFailure("backupFilePath");
    store.appendAuditFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure));

    assertEquals(failure, service(store).restoreBook(book, backup, backupKey).requireRejected());
  }

  @Test
  void inspectRekeyRollback_isSideEffectFree() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
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
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
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
    Path book = path("book.sqlite");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), null).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.NoRollbackArtifactsFound.class, rejected.rejection());
  }

  @Test
  void deleteRekeyRollback_requiresOneExplicitSelectionWhenManyRollbackArtifactsExist() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollbackOne = path("book.rekey-rollback-1.sqlite");
    Path rollbackTwo = path("book.rekey-rollback-2.sqlite");
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
    Path book = path("book.sqlite");
    Path missingRollback = path("book.rekey-rollback-1.sqlite");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(access(book), missingRollback).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.RollbackArtifactNotFound.class, rejected.rejection());
  }

  @Test
  void deleteRekeyRollback_rejectsOneRollbackArtifactThatDoesNotBelongToTheBook() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path foreignRollback = path("foreign.rekey-rollback-1.sqlite");
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
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
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
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
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
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
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
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
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
                    book, rollback, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
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
    Path book = path("book.sqlite");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, null, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
                .requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.NoRollbackArtifactsFound.class, rejected.rejection());
  }

  @Test
  void restoreRekeyRollback_requiresOneExplicitSelectionWhenManyRollbackArtifactsExist() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollbackOne = path("book.rekey-rollback-1.sqlite");
    Path rollbackTwo = path("book.rekey-rollback-2.sqlite");
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
                    book, null, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
                .requireAccepted());

    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactSelectionRequired.class, rejected.rejection());
  }

  @Test
  void restoreRekeyRollback_rejectsOneLiveBookWithNonRollbackBlockingArtifacts() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
    Path wal = path("book.sqlite-wal");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.bookBlockingArtifacts.put(
        store.normalized(book), List.of(store.normalized(rollback), store.normalized(wal)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
                .requireAccepted());

    BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(wal)), blockingArtifacts.blockingArtifactPaths());
  }

  @Test
  void restoreRekeyRollback_rejectsOneBusyLiveBookLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.busyManagedArtifacts.add(store.normalized(book));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
  }

  @Test
  void restoreRekeyRollback_rejectsOneBusyRollbackArtifactLease() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.busyExistingArtifacts.add(store.normalized(rollback));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
                .requireAccepted());

    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, busy.artifactRole());
  }

  @Test
  void restoreRekeyRollback_rejectsOneInvalidRestoredTargetVerification() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
    Path staged = path("staged-rollback.sqlite");
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
                    book, rollback, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
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
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
    Path staged = path("staged-rollback.sqlite");
    touch(rollback);
    store.rollbackArtifacts.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.stagedReplacementPath = staged;

    RekeyRollbackResult.Restored restored =
        assertInstanceOf(
            RekeyRollbackResult.Restored.class,
            service(store)
                .restoreRekeyRollback(
                    book, null, new BookAccess.PassphraseSource.KeyFile(path("book.key")))
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
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    ContractFailure failure = runtimeFailure("backupFilePath");
    store.stageBackupFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(failure));

    assertEquals(
        failure, service(store).backupBook(access(book), backup, backupKey).requireRejected());
  }

  @Test
  void backupBook_appendsOneCompensatingAuditWhenBackupPublicationFails() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    store.failBackupPairCommit = true;

    ContractFailure failure =
        service(store).backupBook(access(book), backup, backupKey).requireRejected();

    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, failure.descriptor());
    assertEquals(1, store.recordedAudits.size());
    assertEquals(1, store.compensatedAudits.size());
  }

  @Test
  void backupBook_surfacesOneAuditCompensationFailureAfterBackupPublicationFailure() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    ContractFailure compensateFailure = runtimeFailure("bookFilePath");
    store.failBackupPairCommit = true;
    store.compensateAuditFailure =
        MaintenanceDecision.failed(MaintenanceFailure.fromContractFailure(compensateFailure));

    ContractFailure failure =
        service(store).backupBook(access(book), backup, backupKey).requireRejected();

    assertEquals(compensateFailure, failure);
    assertEquals(1, store.recordedAudits.size());
    assertTrue(store.compensatedAudits.isEmpty());
  }

  @Test
  void deleteRekeyRollback_appendsOneCompensatingAuditWhenDeletionCommitFails() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
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

  private static ProtectedBookMaintenanceService service(FakeMaintenanceStore store) {
    return new ProtectedBookMaintenanceService(FIXED_CLOCK, store);
  }

  private static BookAccess access(Path bookFilePath) {
    return new BookAccess(
        bookFilePath,
        new BookAccess.PassphraseSource.KeyFile(
            bookFilePath.resolveSibling("book-passphrase.key").toAbsolutePath().normalize()));
  }

  private Path path(String relativePath) {
    return tempDirectory.resolve(relativePath).toAbsolutePath().normalize();
  }

  private static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  private static ContractFailure runtimeFailure(String argument) {
    return new ContractFailure(
        ContractErrors.Descriptor.RUNTIME_FAILURE, "maintenance failure", "retry later", argument);
  }

  private void touch(Path path) {
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      if (!Files.exists(path)) {
        Files.createFile(path);
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Unable to create test file: " + path, exception);
    }
  }

  private record RecordedAudit(Path bookPath, Instant recordedAt, Enum<?> auditKind) {
    private RecordedAudit {
      Objects.requireNonNull(bookPath, "bookPath");
      Objects.requireNonNull(recordedAt, "recordedAt");
      Objects.requireNonNull(auditKind, "auditKind");
    }
  }

  /** Deterministic protected-book maintenance store fixture for service-path tests. */
  private static final class FakeMaintenanceStore implements ProtectedBookMaintenanceStore {
    private final Map<Path, List<Path>> rollbackArtifacts = new ConcurrentHashMap<>();
    private final Map<Path, List<Path>> bookBlockingArtifacts = new ConcurrentHashMap<>();
    private final Map<Path, List<Path>> backupBlockingArtifacts = new ConcurrentHashMap<>();
    private final Map<Path, MaintenanceDecision<BookVerification>> verifications =
        new ConcurrentHashMap<>();
    private final Set<Path> busyManagedArtifacts = ConcurrentHashMap.newKeySet();
    private final Set<Path> busyExistingArtifacts = ConcurrentHashMap.newKeySet();
    private final List<RecordedAudit> recordedAudits = new ArrayList<>();
    private final List<RecordedAudit> compensatedAudits = new ArrayList<>();
    private @Nullable MaintenanceDecision<StagedBackupPair> stageBackupFailure;
    private @Nullable MaintenanceDecision<MaintenanceCompletion> appendAuditFailure;
    private @Nullable MaintenanceDecision<MaintenanceCompletion> compensateAuditFailure;
    private @Nullable Path stagedReplacementPath;
    private boolean failBackupPairCommit;
    private boolean failRollbackDeletionCommit;
    private boolean lastStagedBackupPairCommitted;
    private boolean lastStagedBackupPairClosed;
    private boolean lastRollbackDeletionCommitted;
    private boolean lastRollbackDeletionClosed;

    private Path normalized(Path path) {
      return path.toAbsolutePath().normalize();
    }

    @Override
    public Path normalize(Path path, String argumentName) {
      Objects.requireNonNull(path, argumentName);
      return normalized(path);
    }

    @Override
    public List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
      return bookBlockingArtifacts.getOrDefault(normalizedBookPath, List.of());
    }

    @Override
    public List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
      return backupBlockingArtifacts.getOrDefault(normalizedBackupFilePath, List.of());
    }

    @Override
    public LeaseAcquisition acquireExistingArtifactLease(Path normalizedArtifactPath) {
      if (busyExistingArtifacts.contains(normalizedArtifactPath)) {
        return new LeaseBusy(normalizedArtifactPath);
      }
      return new FakeLease(normalizedArtifactPath);
    }

    @Override
    public LeaseAcquisition acquireManagedArtifactLease(Path normalizedArtifactPath) {
      if (busyManagedArtifacts.contains(normalizedArtifactPath)) {
        return new LeaseBusy(normalizedArtifactPath);
      }
      return new FakeLease(normalizedArtifactPath);
    }

    @Override
    public MaintenanceDecision<BookVerification> verifyInitializedBook(
        ProtectedBookAccess bookAccess) {
      return verifications.getOrDefault(
          normalized(bookAccess.bookFilePath()),
          MaintenanceDecision.accepted(new VerifiedBook(normalized(bookAccess.bookFilePath()))));
    }

    @Override
    public MaintenanceDecision<StagedBackupPair> stageBackupPair(
        ProtectedBookAccess sourceAccess,
        Path normalizedBackupFilePath,
        Path normalizedBackupBookKeyFilePath) {
      if (stageBackupFailure != null) {
        return stageBackupFailure;
      }
      BookVerification stagedBackupVerification =
          switch (verifications.getOrDefault(
              normalized(normalizedBackupFilePath),
              MaintenanceDecision.accepted(
                  new VerifiedBook(normalized(normalizedBackupFilePath))))) {
            case MaintenanceDecision.Accepted<BookVerification>(BookVerification verification) ->
                verification;
            case MaintenanceDecision.Failed<BookVerification>(MaintenanceFailure failure) ->
                throw new AssertionError(
                    "Expected accepted staged-backup verification but got " + failure);
          };
      return MaintenanceDecision.accepted(new FakeStagedBackupPair(stagedBackupVerification));
    }

    @Override
    public StagedBookReplacement stageReplacement(
        Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
      Path stagedBookPath =
          stagedReplacementPath != null
              ? normalized(stagedReplacementPath)
              : normalized(normalizedSourceBookPath);
      stagedReplacementPath = null;
      return new FakeStagedBookReplacement(stagedBookPath);
    }

    @Override
    public List<Path> staleRollbackArtifacts(Path normalizedBookPath) {
      return rollbackArtifacts.getOrDefault(normalizedBookPath, List.of());
    }

    @Override
    public boolean isRollbackArtifactForBook(
        Path normalizedBookPath, Path normalizedRollbackArtifactPath) {
      return staleRollbackArtifacts(normalizedBookPath).contains(normalizedRollbackArtifactPath);
    }

    @Override
    public StagedRollbackArtifactDeletion stageRollbackArtifactDeletion(
        Path normalizedRollbackArtifactPath) {
      return new FakeStagedRollbackArtifactDeletion();
    }

    @Override
    public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAudit(
        ProtectedBookAccess bookAccess,
        Instant recordedAt,
        ProtectedBookMaintenanceAuditKind auditKind) {
      if (appendAuditFailure != null) {
        return appendAuditFailure;
      }
      recordedAudits.add(
          new RecordedAudit(normalized(bookAccess.bookFilePath()), recordedAt, auditKind));
      return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
    }

    @Override
    public MaintenanceDecision<MaintenanceCompletion> appendMaintenanceAuditCompensation(
        ProtectedBookAccess bookAccess,
        Instant recordedAt,
        ProtectedBookMaintenanceAuditCompensationKind auditKind) {
      if (compensateAuditFailure != null) {
        return compensateAuditFailure;
      }
      compensatedAudits.add(
          new RecordedAudit(normalized(bookAccess.bookFilePath()), recordedAt, auditKind));
      return MaintenanceDecision.accepted(MaintenanceCompletion.DONE);
    }

    /** Deterministic held-lease fixture for one normalized artifact path. */
    private static final class FakeLease implements HeldLease {
      private final Path artifactPath;

      private FakeLease(Path artifactPath) {
        this.artifactPath = artifactPath;
      }

      @Override
      public Path artifactPath() {
        return artifactPath;
      }

      @Override
      public void close() {}
    }

    /** Deterministic staged-backup fixture that can publish or fail on demand. */
    private final class FakeStagedBackupPair implements StagedBackupPair {
      private final BookVerification backupVerification;

      private FakeStagedBackupPair(BookVerification backupVerification) {
        this.backupVerification = backupVerification;
      }

      @Override
      public MaintenanceDecision<BookVerification> verifyInitializedBackup() {
        return MaintenanceDecision.accepted(backupVerification);
      }

      @Override
      public void commit() {
        if (failBackupPairCommit) {
          throw new IllegalStateException("backup pair publish failed");
        }
        lastStagedBackupPairCommitted = true;
      }

      @Override
      public void rollback() {}

      @Override
      public void close() {
        lastStagedBackupPairClosed = true;
      }
    }

    /** Deterministic staged-replacement fixture for restore-style maintenance flows. */
    private static final class FakeStagedBookReplacement implements StagedBookReplacement {
      private final Path stagedBookPath;

      private FakeStagedBookReplacement(Path stagedBookPath) {
        this.stagedBookPath = stagedBookPath;
      }

      @Override
      public Path stagedBookPath() {
        return stagedBookPath;
      }

      @Override
      public void commit() {}

      @Override
      public void rollback() {}

      @Override
      public void close() {}
    }

    /** Deterministic staged rollback-artifact deletion fixture. */
    private final class FakeStagedRollbackArtifactDeletion
        implements StagedRollbackArtifactDeletion {
      @Override
      public void commit() {
        if (failRollbackDeletionCommit) {
          throw new IllegalStateException("rollback deletion failed");
        }
        lastRollbackDeletionCommitted = true;
      }

      @Override
      public void rollback() {}

      @Override
      public void close() {
        lastRollbackDeletionClosed = true;
      }
    }
  }
}
