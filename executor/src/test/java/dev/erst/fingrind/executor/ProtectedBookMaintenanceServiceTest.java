package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceArtifactRole;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceVerificationFailure;
import dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEvent;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceEventKind;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceVerificationFailure;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit tests for {@link ProtectedBookMaintenanceService}. */
class ProtectedBookMaintenanceServiceTest {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-05-18T16:30:00Z"), ZoneOffset.UTC);

  @TempDir Path tempDir;

  @Test
  void constructor_rejectsNullDependencies() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    assertThrows(
        NullPointerException.class, () -> new ProtectedBookMaintenanceService(nullOf(), store));
    assertThrows(
        NullPointerException.class,
        () -> new ProtectedBookMaintenanceService(FIXED_CLOCK, nullOf()));
  }

  @Test
  void backupBook_coversDeterministicOutcomesAndStoreFailures() throws IOException {
    Path book = path("book.sqlite");
    Path backup = path("backup.sqlite");
    Path backupKey = path("backup.book-key");
    Path blockingArtifact = path("book.sqlite-wal");
    assertBackupBlockingArtifactsAreRejected(book, backup, backupKey, blockingArtifact);
    assertExistingBackupDestinationIsRejected(book, backupKey);
    assertExistingBackupKeyIsRejected(book);
    assertBusyLiveBookIsRejectedForBackup(book, backup, backupKey);
    assertInvalidLiveBookIsRejectedForBackup(book, backup, backupKey);
    assertBackupVerificationFailurePropagates(book, backup, backupKey);
    assertBackupPublicationFailurePropagates(book, backup, backupKey);
    assertBackupSuccessPublishesPairAndRecordsEvent(book, backup, backupKey);
  }

  @Test
  void restoreBook_coversDeterministicOutcomesAndStoreFailures() throws IOException {
    Path book = path("live-book.sqlite");
    Path backup = path("backup-source.sqlite");
    Path backupKey = path("backup-source.book-key");
    Path bookSidecar = path("live-book.sqlite-wal");
    Path backupSidecar = path("backup-source.sqlite-wal");

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      store.bookBlockingArtifacts.put(
          store.normalized(book), List.of(store.normalized(bookSidecar)));

      RestoreBookResult.Rejected rejected =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              service(store).restoreBook(book, backup, backupKey).requireAccepted());
      BookMaintenanceRejection.BookHasBlockingArtifacts blocking =
          assertInstanceOf(
              BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
      assertEquals(List.of(hint(bookSidecar)), blocking.blockingArtifactPaths());
    }

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      store.backupBlockingArtifacts.put(
          store.normalized(backup), List.of(store.normalized(backupSidecar)));

      RestoreBookResult.Rejected rejected =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              service(store).restoreBook(book, backup, backupKey).requireAccepted());
      BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blocking =
          assertInstanceOf(
              BookMaintenanceRejection.BackupSourceHasBlockingArtifacts.class,
              rejected.rejection());
      assertEquals(List.of(hint(backupSidecar)), blocking.blockingArtifactPaths());
    }

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      store.leaseOutcomes.put(
          store.normalized(book),
          new ProtectedBookMaintenanceStore.LeaseBusy(store.normalized(book)));

      RestoreBookResult.Rejected rejected =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              service(store).restoreBook(book, backup, backupKey).requireAccepted());
      BookMaintenanceRejection.ArtifactBusy busy =
          assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
      assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
    }

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      store.leaseOutcomes.put(
          store.normalized(backup),
          new ProtectedBookMaintenanceStore.LeaseBusy(store.normalized(backup)));

      RestoreBookResult.Rejected rejected =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              service(store).restoreBook(book, backup, backupKey).requireAccepted());
      BookMaintenanceRejection.ArtifactBusy busy =
          assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
      assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, busy.artifactRole());
      assertEquals(hint(backup), busy.artifactPath());
    }

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      store.enqueueVerificationFailure(
          backup, ProtectedBookMaintenanceVerificationFailure.FOREIGN_SQLITE);

      RestoreBookResult.Rejected rejected =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              service(store).restoreBook(book, backup, backupKey).requireAccepted());
      BookMaintenanceRejection.ArtifactVerificationFailed failed =
          assertInstanceOf(
              BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
      assertEquals(BookMaintenanceArtifactRole.BACKUP_SOURCE, failed.artifactRole());
      assertEquals(BookMaintenanceVerificationFailure.FOREIGN_SQLITE, failed.verificationFailure());
    }

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      ContractFailure failure = runtimeFailure("backupFilePath");
      store.enqueueVerificationDecision(backup, ContractDecision.rejected(failure));

      assertSame(failure, service(store).restoreBook(book, backup, backupKey).requireRejected());
    }

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      store.enqueueVerificationDecision(
          backup,
          ContractDecision.accepted(
              new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(backup))));
      store.enqueueVerificationFailure(
          book, ProtectedBookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND);

      RestoreBookResult.Rejected rejected =
          assertInstanceOf(
              RestoreBookResult.Rejected.class,
              service(store).restoreBook(book, backup, backupKey).requireAccepted());
      BookMaintenanceRejection.ArtifactVerificationFailed failed =
          assertInstanceOf(
              BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
      assertEquals(BookMaintenanceArtifactRole.RESTORED_TARGET, failed.artifactRole());
      assertEquals(
          BookMaintenanceVerificationFailure.INCOMPLETE_FINGRIND, failed.verificationFailure());
      assertTrue(store.lastReplacementRolledBack());
      assertFalse(store.lastReplacementCommitted());
    }

    {
      FakeMaintenanceStore store = new FakeMaintenanceStore();
      store.enqueueVerificationDecision(
          backup,
          ContractDecision.accepted(
              new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(backup))));
      store.enqueueVerificationDecision(
          book,
          ContractDecision.accepted(
              new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(book))));

      RestoreBookResult.Restored restored =
          assertInstanceOf(
              RestoreBookResult.Restored.class,
              service(store).restoreBook(book, backup, backupKey).requireAccepted());
      assertEquals(hint(book), restored.bookFilePath());
      assertEquals(hint(backup), restored.backupFilePath());
      assertEquals(hint(backupKey), restored.backupBookKeyFilePath());
      assertTrue(store.lastReplacementCommitted());
      assertFalse(store.lastReplacementRolledBack());
      assertEquals(1, store.recordedEvents.size());
      assertEquals(
          ProtectedBookMaintenanceEventKind.BACKUP_RESTORED,
          store.recordedEvents.getFirst().kind());
    }
  }

  @Test
  void recoverRekey_coversInspectRestoreAndDeleteOutcomes() throws IOException {
    Path book = path("book.sqlite");
    Path rollback = path("book.rekey-rollback-1.sqlite");
    Path secondRollback = path("book.rekey-rollback-2.sqlite");
    Path bookSidecar = path("book.sqlite-wal");
    Files.writeString(rollback, "rollback-one");
    Files.writeString(secondRollback, "rollback-two");
    assertRollbackInspectionReturnsArtifactsAndRecordsEvent(book, rollback, secondRollback);
    assertRollbackRestoreRequiresExpectedPassphrase(book, rollback);
    assertRollbackRestoreRejectsWhenNoArtifactsExist(book);
    assertRollbackRestoreUsesImplicitSingleArtifact(book, rollback);
    assertRollbackRestoreRequiresSelectionForMultipleArtifacts(book, rollback, secondRollback);
    assertRollbackRestoreRejectsMissingNamedArtifact(book);
    assertRollbackRestoreRejectsArtifactForWrongBook(book, rollback);
    assertRollbackRestoreRejectsLiveBookSidecars(book, rollback, bookSidecar);
    assertRollbackRestoreRejectsBusyLiveBook(book, rollback);
    assertRollbackRestoreRejectsBusyRollbackArtifact(book, rollback);
    assertRollbackRestoreRejectsInvalidRollbackArtifact(book, rollback);
    assertRollbackRestoreRejectsInvalidRestoredTarget(book, rollback);
    assertRollbackRestoreSucceedsAndRecordsEvent(book, rollback);
    assertRollbackDeleteRejectsWhenNoArtifactsExist(book);
    assertRollbackDeleteSucceedsAndRecordsEvent(book, rollback);
  }

  private void assertBackupBlockingArtifactsAreRejected(
      Path book, Path backup, Path backupKey, Path blockingArtifact) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.bookBlockingArtifacts.put(
        store.normalized(book), List.of(store.normalized(blockingArtifact)));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());
    BookMaintenanceRejection.BookHasBlockingArtifacts blocking =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(hint(book), blocking.bookFilePath());
    assertEquals(List.of(hint(blockingArtifact)), blocking.blockingArtifactPaths());
  }

  private void assertExistingBackupDestinationIsRejected(Path book, Path backupKey)
      throws IOException {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path occupiedBackup = path("occupied-backup.sqlite");
    Files.writeString(occupiedBackup, "occupied");

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), occupiedBackup, backupKey).requireAccepted());
    BookMaintenanceRejection.BackupDestinationAlreadyExists destinationExists =
        assertInstanceOf(
            BookMaintenanceRejection.BackupDestinationAlreadyExists.class, rejected.rejection());
    assertEquals(hint(occupiedBackup), destinationExists.backupFilePath());
  }

  private void assertExistingBackupKeyIsRejected(Path book) throws IOException {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path freshBackup = path("fresh-backup.sqlite");
    Path occupiedBackupKey = path("occupied-backup.book-key");
    Files.writeString(occupiedBackupKey, "occupied");

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store)
                .backupBook(access(book), freshBackup, occupiedBackupKey)
                .requireAccepted());
    BookMaintenanceRejection.BackupKeyFileAlreadyExists keyExists =
        assertInstanceOf(
            BookMaintenanceRejection.BackupKeyFileAlreadyExists.class, rejected.rejection());
    assertEquals(hint(occupiedBackupKey), keyExists.backupBookKeyFilePath());
  }

  private void assertBusyLiveBookIsRejectedForBackup(Path book, Path backup, Path backupKey) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.leaseOutcomes.put(
        store.normalized(book),
        new ProtectedBookMaintenanceStore.LeaseBusy(store.normalized(book)));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());
    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
    assertEquals(hint(book), busy.artifactPath());
  }

  private void assertInvalidLiveBookIsRejectedForBackup(Path book, Path backup, Path backupKey) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.enqueueVerificationFailure(book, ProtectedBookMaintenanceVerificationFailure.MISSING);

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());
    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, failed.artifactRole());
    assertEquals(hint(book), failed.artifactPath());
    assertEquals(BookMaintenanceVerificationFailure.MISSING, failed.verificationFailure());
  }

  private void assertBackupVerificationFailurePropagates(Path book, Path backup, Path backupKey) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    ContractFailure failure = runtimeFailure("bookFilePath");
    store.enqueueVerificationDecision(book, ContractDecision.rejected(failure));

    assertSame(
        failure, service(store).backupBook(access(book), backup, backupKey).requireRejected());
  }

  private void assertBackupPublicationFailurePropagates(Path book, Path backup, Path backupKey) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    ContractFailure failure = runtimeFailure("backupFilePath");
    store.publishBackupDecision = ContractDecision.rejected(failure);

    assertSame(
        failure, service(store).backupBook(access(book), backup, backupKey).requireRejected());
  }

  private void assertBackupSuccessPublishesPairAndRecordsEvent(
      Path book, Path backup, Path backupKey) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();

    BackupBookResult.BackedUp backedUp =
        assertInstanceOf(
            BackupBookResult.BackedUp.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());
    assertEquals(hint(book), backedUp.bookFilePath());
    assertEquals(hint(backup), backedUp.backupFilePath());
    assertEquals(hint(backupKey), backedUp.backupBookKeyFilePath());
    BookAccess publishedSourceAccess =
        Objects.requireNonNull(store.publishedSourceAccess, "publishedSourceAccess");
    assertEquals(store.normalized(book), publishedSourceAccess.bookFilePath());
    assertEquals(store.normalized(backup), store.publishedBackupFilePath);
    assertEquals(store.normalized(backupKey), store.publishedBackupBookKeyFilePath);
    assertEquals(1, store.recordedEvents.size());
    ProtectedBookMaintenanceEvent event = store.recordedEvents.getFirst();
    assertEquals(ProtectedBookMaintenanceEventKind.BACKUP_CREATED, event.kind());
    assertEquals(store.normalized(book), event.bookFilePath());
    assertEquals(store.normalized(backup), event.backupFilePath());
    assertEquals(store.normalized(backupKey), event.backupBookKeyFilePath());
  }

  private void assertRollbackInspectionReturnsArtifactsAndRecordsEvent(
      Path book, Path rollback, Path secondRollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackArtifactsByBook.put(
        store.normalized(book),
        List.of(store.normalized(rollback), store.normalized(secondRollback)));

    RekeyRollbackResult.Inspected inspected =
        assertInstanceOf(
            RekeyRollbackResult.Inspected.class,
            service(store).inspectRekeyRollback(book).requireAccepted());
    assertEquals(hint(book), inspected.bookFilePath());
    assertEquals(List.of(hint(rollback), hint(secondRollback)), inspected.rollbackArtifactPaths());
    assertEquals(
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_INSPECTED,
        store.recordedEvents.getFirst().kind());
  }

  private void assertRollbackRestoreRequiresExpectedPassphrase(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    assertThrows(
        NullPointerException.class,
        () -> service(store).restoreRekeyRollback(book, rollback, nullOf()));
  }

  private void assertRollbackRestoreRejectsWhenNoArtifactsExist(Path book) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, null, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    assertInstanceOf(BookMaintenanceRejection.NoRollbackArtifactsFound.class, rejected.rejection());
  }

  private void assertRollbackRestoreUsesImplicitSingleArtifact(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackArtifactsByBook.put(store.normalized(book), List.of(store.normalized(rollback)));
    store.enqueueVerificationDecision(
        rollback,
        ContractDecision.accepted(
            new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(rollback))));
    store.enqueueVerificationDecision(
        book,
        ContractDecision.accepted(
            new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(book))));

    RekeyRollbackResult.Restored restored =
        assertInstanceOf(
            RekeyRollbackResult.Restored.class,
            service(store)
                .restoreRekeyRollback(
                    book, null, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(hint(rollback), restored.rollbackArtifactPath());
  }

  private void assertRollbackRestoreRequiresSelectionForMultipleArtifacts(
      Path book, Path rollback, Path secondRollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackArtifactsByBook.put(
        store.normalized(book),
        List.of(store.normalized(rollback), store.normalized(secondRollback)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, null, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired =
        assertInstanceOf(
            BookMaintenanceRejection.RollbackArtifactSelectionRequired.class, rejected.rejection());
    assertEquals(
        List.of(hint(rollback), hint(secondRollback)), selectionRequired.rollbackArtifactPaths());
  }

  private void assertRollbackRestoreRejectsMissingNamedArtifact(Path book) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path missingRollback = path("missing.rekey-rollback.sqlite");

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, missingRollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    BookMaintenanceRejection.RollbackArtifactNotFound notFound =
        assertInstanceOf(
            BookMaintenanceRejection.RollbackArtifactNotFound.class, rejected.rejection());
    assertEquals(hint(missingRollback), notFound.rollbackArtifactPath());
  }

  private void assertRollbackRestoreRejectsArtifactForWrongBook(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), false);

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.RollbackArtifactNotForBook.class, rejected.rejection());
  }

  private void assertRollbackRestoreRejectsLiveBookSidecars(
      Path book, Path rollback, Path bookSidecar) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), true);
    store.bookBlockingArtifacts.put(
        store.normalized(book), List.of(store.normalized(rollback), store.normalized(bookSidecar)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    BookMaintenanceRejection.BookHasBlockingArtifacts blocking =
        assertInstanceOf(
            BookMaintenanceRejection.BookHasBlockingArtifacts.class, rejected.rejection());
    assertEquals(List.of(hint(bookSidecar)), blocking.blockingArtifactPaths());
  }

  private void assertRollbackRestoreRejectsBusyLiveBook(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), true);
    store.leaseOutcomes.put(
        store.normalized(book),
        new ProtectedBookMaintenanceStore.LeaseBusy(store.normalized(book)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.LIVE_BOOK, busy.artifactRole());
  }

  private void assertRollbackRestoreRejectsBusyRollbackArtifact(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), true);
    store.leaseOutcomes.put(
        store.normalized(rollback),
        new ProtectedBookMaintenanceStore.LeaseBusy(store.normalized(rollback)));

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    BookMaintenanceRejection.ArtifactBusy busy =
        assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, busy.artifactRole());
    assertEquals(hint(rollback), busy.artifactPath());
  }

  private void assertRollbackRestoreRejectsInvalidRollbackArtifact(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), true);
    store.enqueueVerificationFailure(
        rollback, ProtectedBookMaintenanceVerificationFailure.BLANK_SQLITE);

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, failed.artifactRole());
    assertEquals(BookMaintenanceVerificationFailure.BLANK_SQLITE, failed.verificationFailure());
  }

  private void assertRollbackRestoreRejectsInvalidRestoredTarget(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), true);
    store.enqueueVerificationDecision(
        rollback,
        ContractDecision.accepted(
            new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(rollback))));
    store.enqueueVerificationFailure(
        book, ProtectedBookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED);

    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    BookMaintenanceRejection.ArtifactVerificationFailed failed =
        assertInstanceOf(
            BookMaintenanceRejection.ArtifactVerificationFailed.class, rejected.rejection());
    assertEquals(BookMaintenanceArtifactRole.RESTORED_TARGET, failed.artifactRole());
    assertEquals(
        BookMaintenanceVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED,
        failed.verificationFailure());
    assertTrue(store.lastReplacementRolledBack());
  }

  private void assertRollbackRestoreSucceedsAndRecordsEvent(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), true);
    store.enqueueVerificationDecision(
        rollback,
        ContractDecision.accepted(
            new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(rollback))));
    store.enqueueVerificationDecision(
        book,
        ContractDecision.accepted(
            new ProtectedBookMaintenanceStore.VerifiedBook(store.normalized(book))));

    RekeyRollbackResult.Restored restored =
        assertInstanceOf(
            RekeyRollbackResult.Restored.class,
            service(store)
                .restoreRekeyRollback(
                    book, rollback, BookAccess.PassphraseSource.StandardInput.INSTANCE)
                .requireAccepted());
    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(hint(rollback), restored.rollbackArtifactPath());
    assertTrue(store.lastReplacementCommitted());
    assertEquals(
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_RESTORED,
        store.recordedEvents.getFirst().kind());
  }

  private void assertRollbackDeleteRejectsWhenNoArtifactsExist(Path book) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    RekeyRollbackResult.Rejected rejected =
        assertInstanceOf(
            RekeyRollbackResult.Rejected.class,
            service(store).deleteRekeyRollback(book, null).requireAccepted());
    assertInstanceOf(BookMaintenanceRejection.NoRollbackArtifactsFound.class, rejected.rejection());
  }

  private void assertRollbackDeleteSucceedsAndRecordsEvent(Path book, Path rollback) {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    store.rollbackBelongs.put(store.normalized(rollback), true);

    RekeyRollbackResult.Deleted deleted =
        assertInstanceOf(
            RekeyRollbackResult.Deleted.class,
            service(store).deleteRekeyRollback(book, rollback).requireAccepted());
    assertEquals(hint(book), deleted.bookFilePath());
    assertEquals(hint(rollback), deleted.rollbackArtifactPath());
    assertEquals(List.of(store.normalized(rollback)), store.deletedRollbackArtifacts);
    assertEquals(
        ProtectedBookMaintenanceEventKind.REKEY_ROLLBACK_DELETED,
        store.recordedEvents.getFirst().kind());
  }

  private ProtectedBookMaintenanceService service(FakeMaintenanceStore store) {
    return new ProtectedBookMaintenanceService(FIXED_CLOCK, store);
  }

  private BookAccess access(Path book) {
    return new BookAccess(book, BookAccess.PassphraseSource.StandardInput.INSTANCE);
  }

  private Path path(String fileName) {
    return tempDir.resolve(fileName);
  }

  private static ContractFailure runtimeFailure(String argument) {
    return ContractErrors.Descriptor.RUNTIME_FAILURE.failure("runtime failure", null, argument);
  }

  private static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }

  /** In-memory maintenance store double that captures orchestration inputs and outcomes. */
  private static final class FakeMaintenanceStore implements ProtectedBookMaintenanceStore {
    final ConcurrentMap<Path, List<Path>> bookBlockingArtifacts = new ConcurrentHashMap<>();
    final ConcurrentMap<Path, List<Path>> backupBlockingArtifacts = new ConcurrentHashMap<>();
    final ConcurrentMap<Path, LeaseAcquisition> leaseOutcomes = new ConcurrentHashMap<>();
    final ConcurrentMap<Path, List<Path>> rollbackArtifactsByBook = new ConcurrentHashMap<>();
    final ConcurrentMap<Path, Boolean> rollbackBelongs = new ConcurrentHashMap<>();
    final ConcurrentMap<Path, Deque<ContractDecision<BookVerification>>> verificationDecisions =
        new ConcurrentHashMap<>();
    final List<ProtectedBookMaintenanceEvent> recordedEvents = new ArrayList<>();
    final List<Path> deletedRollbackArtifacts = new ArrayList<>();
    ContractDecision<Path> publishBackupDecision = ContractDecision.accepted(Path.of("/ignored"));
    @Nullable BookAccess publishedSourceAccess;
    @Nullable Path publishedBackupFilePath;
    @Nullable Path publishedBackupBookKeyFilePath;
    @Nullable FakePreparedBookReplacement lastReplacement;

    Path normalized(Path path) {
      return path.toAbsolutePath().normalize();
    }

    void enqueueVerificationDecision(Path path, ContractDecision<BookVerification> decision) {
      verificationDecisions
          .computeIfAbsent(normalized(path), ignored -> new ArrayDeque<>())
          .addLast(decision);
    }

    void enqueueVerificationFailure(
        Path path, ProtectedBookMaintenanceVerificationFailure failure) {
      enqueueVerificationDecision(
          path, ContractDecision.accepted(new VerificationFailure(normalized(path), failure)));
    }

    @Override
    public Path normalize(Path path, String argumentName) {
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
    public LeaseAcquisition acquireExclusiveLease(Path normalizedArtifactPath) {
      return leaseOutcomes.getOrDefault(
          normalizedArtifactPath, new FakeHeldLease(normalizedArtifactPath));
    }

    @Override
    public ContractDecision<BookVerification> verifyInitializedBook(BookAccess bookAccess) {
      Deque<ContractDecision<BookVerification>> queued =
          verificationDecisions.get(normalized(bookAccess.bookFilePath()));
      if (queued != null && !queued.isEmpty()) {
        return queued.removeFirst();
      }
      return ContractDecision.accepted(new VerifiedBook(normalized(bookAccess.bookFilePath())));
    }

    @Override
    public ContractDecision<Path> publishBackupPair(
        BookAccess sourceAccess,
        Path normalizedBackupFilePath,
        Path normalizedBackupBookKeyFilePath) {
      publishedSourceAccess = sourceAccess;
      publishedBackupFilePath = normalizedBackupFilePath;
      publishedBackupBookKeyFilePath = normalizedBackupBookKeyFilePath;
      return publishBackupDecision;
    }

    @Override
    public PreparedBookReplacement prepareReplacement(
        Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
      lastReplacement = new FakePreparedBookReplacement(normalizedTargetBookPath);
      return lastReplacement;
    }

    @Override
    public List<Path> staleRollbackArtifacts(Path normalizedBookPath) {
      return rollbackArtifactsByBook.getOrDefault(normalizedBookPath, List.of());
    }

    @Override
    public boolean isRollbackArtifactForBook(
        Path normalizedBookPath, Path normalizedRollbackArtifactPath) {
      return rollbackBelongs.getOrDefault(normalizedRollbackArtifactPath, false);
    }

    @Override
    public void deleteRollbackArtifact(Path normalizedRollbackArtifactPath) {
      deletedRollbackArtifacts.add(normalizedRollbackArtifactPath);
    }

    @Override
    public void recordMaintenanceEvent(ProtectedBookMaintenanceEvent maintenanceEvent) {
      recordedEvents.add(maintenanceEvent);
    }

    boolean lastReplacementCommitted() {
      return Objects.requireNonNull(lastReplacement, "lastReplacement").committed;
    }

    boolean lastReplacementRolledBack() {
      return Objects.requireNonNull(lastReplacement, "lastReplacement").rolledBack;
    }
  }

  /** No-op lease double that carries only the normalized artifact path. */
  private static final class FakeHeldLease implements ProtectedBookMaintenanceStore.HeldLease {
    private final Path artifactPath;

    private FakeHeldLease(Path artifactPath) {
      this.artifactPath = artifactPath;
    }

    @Override
    public Path artifactPath() {
      return artifactPath;
    }

    @Override
    public void close() {}
  }

  /** Replacement-handle double that records commit and rollback decisions for assertions. */
  private static final class FakePreparedBookReplacement
      implements ProtectedBookMaintenanceStore.PreparedBookReplacement {
    private final Path targetBookPath;
    private boolean committed;
    private boolean rolledBack;

    private FakePreparedBookReplacement(Path targetBookPath) {
      this.targetBookPath = targetBookPath;
    }

    @Override
    public Path targetBookPath() {
      return targetBookPath;
    }

    @Override
    public void commit() {
      committed = true;
    }

    @Override
    public void rollback() {
      rolledBack = true;
    }

    @Override
    public void close() {}
  }
}
