package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.access;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.hint;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.path;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.service;
import static dev.erst.fingrind.executor.ProtectedBookMaintenanceTestSupport.touch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Safety contract tests for generated secret targets and explicit restore replacement consent. */
class ProtectedBookMaintenanceDestinationSafetyTest {
  @TempDir Path tempDirectory;

  @Test
  void interruptedPairRecoveryFailure_isTypedBeforeAnyMaintenanceSourceVerification() {
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path newBookKey = path(tempDirectory, "book-new.book-key");

    FakeMaintenanceStore backupStore = new FakeMaintenanceStore();
    backupStore.staging.interruptedPairRecoveryFailure =
        new IllegalStateException("backup recovery failed");
    ContractFailure backupFailure =
        service(backupStore).backupBook(access(book), backup, backupKey).requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, backupFailure.descriptor());
    assertEquals(
        "Failed to recover or prepare the FinGrind backup pair publication.",
        backupFailure.message());
    assertEquals(1, backupStore.staging.interruptedPairRecoveryRequests);
    assertTrue(backupStore.verificationRequests.isEmpty());

    FakeMaintenanceStore restoreStore = new FakeMaintenanceStore();
    restoreStore.staging.interruptedPairRecoveryFailure =
        new IllegalStateException("restore recovery failed");
    ContractFailure restoreFailure =
        service(restoreStore)
            .restoreBook(book, newBookKey, backup, backupKey, false)
            .requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, restoreFailure.descriptor());
    assertEquals(
        "Failed to recover or prepare the FinGrind restored-book pair publication.",
        restoreFailure.message());
    assertEquals(1, restoreStore.staging.interruptedPairRecoveryRequests);
    assertTrue(restoreStore.verificationRequests.isEmpty());

    FakeMaintenanceStore rekeyStore = new FakeMaintenanceStore();
    rekeyStore.staging.interruptedPairRecoveryFailure =
        new IllegalStateException("rekey recovery failed");
    ContractFailure rekeyFailure =
        service(rekeyStore).rekeyBook(access(book), newBookKey).requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, rekeyFailure.descriptor());
    assertEquals(
        "Failed to recover or prepare the FinGrind rekeyed-book pair publication.",
        rekeyFailure.message());
    assertEquals(1, rekeyStore.staging.interruptedPairRecoveryRequests);
    assertTrue(rekeyStore.verificationRequests.isEmpty());
  }

  @Test
  void stagedPairPublicationFailure_isTypedForRestoreAndRekey() {
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path newBookKey = path(tempDirectory, "book-new.book-key");

    FakeMaintenanceStore restoreStore = new FakeMaintenanceStore();
    restoreStore.staging.restoredPairCommitRuntimeFailure =
        new IllegalStateException("restore pair publication failed");
    ContractFailure restoreFailure =
        service(restoreStore)
            .restoreBook(book, newBookKey, backup, backupKey, false)
            .requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, restoreFailure.descriptor());
    assertEquals(
        "Failed to publish the staged FinGrind restored-book pair.", restoreFailure.message());
    assertTrue(restoreStore.staging.restoredPairClosed);

    FakeMaintenanceStore rekeyStore = new FakeMaintenanceStore();
    rekeyStore.staging.restoredPairCommitRuntimeFailure =
        new IllegalStateException("rekey pair publication failed");
    ContractFailure rekeyFailure =
        service(rekeyStore).rekeyBook(access(book), newBookKey).requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, rekeyFailure.descriptor());
    assertEquals(
        "Failed to publish the staged FinGrind rekeyed book pair.", rekeyFailure.message());
    assertTrue(rekeyStore.staging.restoredPairClosed);
  }

  @Test
  void stagingRuntimeFailureAfterPreparation_isTypedAtEachWorkflowBoundary() {
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path newBookKey = path(tempDirectory, "book-new.book-key");

    FakeMaintenanceStore backupStore = new FakeMaintenanceStore();
    backupStore.staging.stagePairRuntimeFailure =
        new IllegalStateException("backup staging failed");
    ContractFailure backupFailure =
        service(backupStore).backupBook(access(book), backup, backupKey).requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, backupFailure.descriptor());
    assertEquals(
        "Failed to verify, stage, or publish the FinGrind backup pair.", backupFailure.message());

    FakeMaintenanceStore restoreStore = new FakeMaintenanceStore();
    restoreStore.staging.stagePairRuntimeFailure =
        new IllegalStateException("restore staging failed");
    ContractFailure restoreFailure =
        service(restoreStore)
            .restoreBook(book, newBookKey, backup, backupKey, false)
            .requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, restoreFailure.descriptor());
    assertEquals(
        "Failed to verify, stage, or publish the FinGrind restored-book pair.",
        restoreFailure.message());

    FakeMaintenanceStore rekeyStore = new FakeMaintenanceStore();
    rekeyStore.staging.stagePairRuntimeFailure = new IllegalStateException("rekey staging failed");
    ContractFailure rekeyFailure =
        service(rekeyStore).rekeyBook(access(book), newBookKey).requireRejected();
    assertEquals(ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE, rekeyFailure.descriptor());
    assertEquals(
        "Failed to verify, stage, or publish the FinGrind rekeyed-book pair.",
        rekeyFailure.message());
  }

  @Test
  void preparedPublicationCloseRejection_remainsAWorkflowRejection() {
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path newBookKey = path(tempDirectory, "book-new.book-key");

    FakeMaintenanceStore backupStore = new FakeMaintenanceStore();
    backupStore.staging.preparedPairCloseRejection =
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(
            backupStore.normalized(backupKey));
    BackupBookResult.Rejected backupRejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(backupStore).backupBook(access(book), backup, backupKey).requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.SecretTargetOccupied.class, backupRejected.rejection());

    FakeMaintenanceStore restoreStore = new FakeMaintenanceStore();
    restoreStore.staging.preparedPairCloseRejection =
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(
            restoreStore.normalized(newBookKey));
    RestoreBookResult.Rejected restoreRejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(restoreStore)
                .restoreBook(book, newBookKey, backup, backupKey, false)
                .requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.SecretTargetOccupied.class, restoreRejected.rejection());

    FakeMaintenanceStore rekeyStore = new FakeMaintenanceStore();
    rekeyStore.staging.preparedPairCloseRejection =
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(
            rekeyStore.normalized(newBookKey));
    RekeyBookResult.Rejected rekeyRejected =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(rekeyStore).rekeyBook(access(book), newBookKey).requireAccepted());
    assertInstanceOf(
        BookMaintenanceRejection.SecretTargetOccupied.class, rekeyRejected.rejection());
  }

  @Test
  void stagedRestorePairPublicationRejection_remainsOneDeterministicRejection() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    store.staging.restoredPairCommitRejection =
        new ProtectedBookMaintenanceRejection.ArtifactBusy(
            dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole
                .RESTORED_TARGET,
            store.normalized(book));

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store)
                .restoreBook(book, newBookKey, backup, backupKey, false)
                .requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.ArtifactBusy.class, rejected.rejection());
    assertTrue(store.staging.restoredPairClosed);
  }

  @Test
  void backupBook_projectsOneDeterministicRejectionRaisedWhilePublishingTheStagedPair() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    store.staging.backupPairCommitRejection =
        new ProtectedBookMaintenanceRejection.SecretTargetOccupied(store.normalized(backupKey));

    BackupBookResult.Rejected rejected =
        assertInstanceOf(
            BackupBookResult.Rejected.class,
            service(store).backupBook(access(book), backup, backupKey).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.SecretTargetOccupied.class, rejected.rejection());
  }

  @Test
  void restoreBook_requiresExplicitConsentBeforeReplacingAnExistingLiveBook() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = path(tempDirectory, "book-new.book-key");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    touch(book);

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.BookDestinationOccupied occupied =
        assertInstanceOf(
            BookMaintenanceRejection.BookDestinationOccupied.class, rejected.rejection());
    assertEquals(hint(book), occupied.bookFilePath());
    assertTrue(store.verificationRequests.isEmpty());
  }

  @Test
  void restoreBook_refusesAnOccupiedGeneratedDestinationKeyBeforeSourceVerification() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = path(tempDirectory, "book-new.book-key");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    touch(bookKey);

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.SecretTargetOccupied occupied =
        assertInstanceOf(BookMaintenanceRejection.SecretTargetOccupied.class, rejected.rejection());
    assertEquals(hint(bookKey), occupied.secretTargetPath());
    assertEquals(1, store.staging.interruptedPairRecoveryRequests);
    assertTrue(store.verificationRequests.isEmpty());
  }

  @Test
  void restoreBook_prioritizesMissingReplacementConsentOverAnyGeneratedKeyCollision() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = path(tempDirectory, "book-new.book-key");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    touch(book);
    touch(bookKey);

    RestoreBookResult.Rejected rejected =
        assertInstanceOf(
            RestoreBookResult.Rejected.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    BookMaintenanceRejection.BookDestinationOccupied occupied =
        assertInstanceOf(
            BookMaintenanceRejection.BookDestinationOccupied.class, rejected.rejection());
    assertEquals(hint(book), occupied.bookFilePath());
    assertTrue(store.verificationRequests.isEmpty());
  }

  @Test
  void backupBook_refusesAnOccupiedGeneratedTargetBeforeSourceVerification() {
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
    assertEquals(1, store.staging.interruptedPairRecoveryRequests);
    assertTrue(store.verificationRequests.isEmpty());
  }

  @Test
  void rekeyBook_refusesAnOccupiedGeneratedTargetBeforeSourceVerification() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path newBookKey = path(tempDirectory, "book-new.book-key");
    touch(newBookKey);

    RekeyBookResult.Rejected rejected =
        assertInstanceOf(
            RekeyBookResult.Rejected.class,
            service(store).rekeyBook(access(book), newBookKey).requireAccepted());

    assertInstanceOf(BookMaintenanceRejection.SecretTargetOccupied.class, rejected.rejection());
    assertEquals(1, store.staging.interruptedPairRecoveryRequests);
    assertTrue(store.verificationRequests.isEmpty());
  }

  @Test
  void restoreBook_replacesAnExistingLiveBookWhenExplicitConsentIsProvided() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = path(tempDirectory, "book-new.book-key");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");
    touch(book);

    RestoreBookResult.Restored restored =
        assertInstanceOf(
            RestoreBookResult.Restored.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, true).requireAccepted());

    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(
        List.of(RestoredBookTargetPolicy.REPLACE_SELECTED),
        store.staging.restoredBookTargetPolicies);
  }

  @Test
  void restoreBook_carriesNoReplacementAuthorityToFinalPublicationWithoutConsent() {
    FakeMaintenanceStore store = new FakeMaintenanceStore();
    Path book = path(tempDirectory, "book.sqlite");
    Path bookKey = path(tempDirectory, "book-new.book-key");
    Path backup = path(tempDirectory, "backup.sqlite");
    Path backupKey = path(tempDirectory, "backup.book-key");

    RestoreBookResult.Restored restored =
        assertInstanceOf(
            RestoreBookResult.Restored.class,
            service(store).restoreBook(book, bookKey, backup, backupKey, false).requireAccepted());

    assertEquals(hint(book), restored.bookFilePath());
    assertEquals(
        List.of(RestoredBookTargetPolicy.REQUIRE_ABSENT), store.staging.restoredBookTargetPolicies);
  }
}
