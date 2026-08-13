package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublication;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookVerificationFailure;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises journal recovery as a read-and-prove operation, never a second maintenance mutation.
 */
class AttestedProtectedBookPairPublicationRecoveryTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-08-11T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void recoversCompletedRestoreAndRekeyOnlyWhenTheirFinalEvidenceMatches() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();

    AttestationMaintenanceTestSupport.Store restoreStore = store(credential);
    backup(restoreStore, credential);
    restore(restoreStore, credential);
    restoreStore.setLiveEvidence(restoreStore.restoredEvidence());
    restoreStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            pairPublication(restoredBookPath(), restoredKeyPath())));
    try (var session = credential.openSession()) {
      ProtectedBookRestoreOutcome.Restored recovered =
          assertInstanceOf(
              ProtectedBookRestoreOutcome.Restored.class,
              accepted(
                  workflow(restoreStore)
                      .restoreBook(
                          restoredBookPath(),
                          restoredKeyPath(),
                          backupPath(),
                          backupKeyPath(),
                          session)));
      assertEquals(
          ProtectedBookPairPublicationCompletion.RECOVERED, recovered.pairPublicationCompletion());
    }

    AttestationMaintenanceTestSupport.Store rekeyStore = store(credential);
    rekey(rekeyStore, credential);
    rekeyStore.setLiveEvidence(rekeyStore.restoredEvidence());
    rekeyStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            pairPublication(bookPath(), rekeyPath())));
    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rekeyed recovered =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rekeyed.class,
              accepted(workflow(rekeyStore).rekeyBook(access(credential), rekeyPath(), session)));
      assertEquals(
          ProtectedBookPairPublicationCompletion.RECOVERED, recovered.pairPublicationCompletion());
    }
  }

  @Test
  void recoversCompletedRekeyWithItsReplacementKeyWhenTheOriginalKeyIsSuperseded()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    AttestationMaintenanceTestSupport.Store rekeyStore = store(credential);
    rekey(rekeyStore, credential);
    rekeyStore.setLiveEvidence(rekeyStore.restoredEvidence());
    rekeyStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            pairPublication(bookPath(), rekeyPath())));
    rekeyStore.rejectVerificationFor(
        access(credential),
        new ProtectedBookMaintenanceRejection.ArtifactVerificationFailed(
            ProtectedBookMaintenanceArtifactRole.LIVE_BOOK,
            bookPath(),
            ProtectedBookVerificationFailure.PROTECTED_BOOK_VERIFICATION_FAILED));

    try (var session = credential.openSession()) {
      ProtectedBookRekeyOutcome.Rekeyed recovered =
          assertInstanceOf(
              ProtectedBookRekeyOutcome.Rekeyed.class,
              accepted(workflow(rekeyStore).rekeyBook(access(credential), rekeyPath(), session)));
      assertEquals(
          ProtectedBookPairPublicationCompletion.RECOVERED, recovered.pairPublicationCompletion());
    }
  }

  @Test
  void failsRecoveryWhenFinalEvidenceDoesNotProveTheRequestedOperation() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();

    AttestationMaintenanceTestSupport.Store restoreStore = store(credential);
    backup(restoreStore, credential);
    restoreStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            pairPublication(restoredBookPath(), restoredKeyPath())));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(restoreStore)
              .restoreBook(
                  restoredBookPath(), restoredKeyPath(), backupPath(), backupKeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store rekeyStore = store(credential);
    rekeyStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            pairPublication(bookPath(), rekeyPath())));
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(rekeyStore).rekeyBook(access(credential), rekeyPath(), session));
    }
  }

  @Test
  void preventsReplacementWhenAdmissionsAreIncompleteOrEvidenceBlocked() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();

    AttestationMaintenanceTestSupport.Store restoreStore = store(credential);
    backup(restoreStore, credential);
    restoreStore.setInjectedPairAdmission(incomplete(restoredBookPath()));
    try (var session = credential.openSession()) {
      assertIncomplete(
          () ->
              workflow(restoreStore)
                  .restoreBook(
                      restoredBookPath(),
                      restoredKeyPath(),
                      backupPath(),
                      backupKeyPath(),
                      session));
    }

    AttestationMaintenanceTestSupport.Store rekeyStore = store(credential);
    rekeyStore.setInjectedPairAdmission(incomplete(bookPath()));
    try (var session = credential.openSession()) {
      assertIncomplete(
          () -> workflow(rekeyStore).rekeyBook(access(credential), rekeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store backupStore = store(credential);
    backupStore.setInjectedPairAdmission(incomplete(backupPath()));
    try (var session = credential.openSession()) {
      assertIncomplete(
          () ->
              workflow(backupStore)
                  .backupBook(
                      access(credential), backupPath(), backupKeyPath(), BACKUP_ID, session));
    }

    AttestationMaintenanceTestSupport.Store blockedRestoreStore = store(credential);
    backup(blockedRestoreStore, credential);
    blockedRestoreStore.setInjectedPairAdmission(blocked(restoredBookPath(), restoredKeyPath()));
    try (var session = credential.openSession()) {
      assertEvidenceBlocked(
          () ->
              workflow(blockedRestoreStore)
                  .restoreBook(
                      restoredBookPath(),
                      restoredKeyPath(),
                      backupPath(),
                      backupKeyPath(),
                      session));
    }

    AttestationMaintenanceTestSupport.Store blockedRekeyStore = store(credential);
    blockedRekeyStore.setInjectedPairAdmission(blocked(bookPath(), rekeyPath()));
    try (var session = credential.openSession()) {
      assertEvidenceBlocked(
          () -> workflow(blockedRekeyStore).rekeyBook(access(credential), rekeyPath(), session));
    }
  }

  @Test
  void resumesARecoveredBackupAndAcknowledgesMatchingExistingBackupTargets() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();

    AttestationMaintenanceTestSupport.Store recoveredStore = store(credential);
    backup(recoveredStore, credential);
    recoveredStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            pairPublication(backupPath(), backupKeyPath())));
    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.BackedUp recovered =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              accepted(
                  workflow(recoveredStore)
                      .backupBook(
                          access(credential), backupPath(), backupKeyPath(), BACKUP_ID, session)));
      assertEquals(
          ProtectedBookPairPublicationCompletion.RECOVERED, recovered.pairPublicationCompletion());
    }

    AttestationMaintenanceTestSupport.Store existingStore = store(credential);
    backup(existingStore, credential);
    existingStore.setInjectedPairAdmission(existingBackup());
    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.BackedUp existing =
          assertInstanceOf(
              ProtectedBookBackupOutcome.BackedUp.class,
              accepted(
                  workflow(existingStore)
                      .backupBook(
                          access(credential), backupPath(), backupKeyPath(), BACKUP_ID, session)));
      assertEquals(
          ProtectedBookPairPublicationCompletion.ALREADY_PUBLISHED,
          existing.pairPublicationCompletion());
    }
  }

  @Test
  void refusesBackupOnlyEvidenceForRestoreAndRekeyAndMismatchedBackupTargets() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();

    AttestationMaintenanceTestSupport.Store restoreStore = store(credential);
    backup(restoreStore, credential);
    restoreStore.setInjectedPairAdmission(existingBackup());
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(restoreStore)
              .restoreBook(
                  restoredBookPath(), restoredKeyPath(), backupPath(), backupKeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store rekeyStore = store(credential);
    rekeyStore.setInjectedPairAdmission(existingBackup());
    try (var session = credential.openSession()) {
      assertInstanceOf(
          MaintenanceDecision.Failed.class,
          workflow(rekeyStore).rekeyBook(access(credential), rekeyPath(), session));
    }

    AttestationMaintenanceTestSupport.Store backupStore = store(credential);
    backupStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
            temporaryDirectory.resolve("other/book.fgba"),
            temporaryDirectory.resolve("other/book.key")));
    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.Rejected.class,
              accepted(
                  workflow(backupStore)
                      .backupBook(
                          access(credential), backupPath(), backupKeyPath(), BACKUP_ID, session)));
      assertInstanceOf(
          dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection
              .BackupDestinationAlreadyExists.class,
          rejected.rejection());
    }

    AttestationMaintenanceTestSupport.Store secretMismatchStore = store(credential);
    secretMismatchStore.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
            backupPath(), temporaryDirectory.resolve("other/book.key")));
    try (var session = credential.openSession()) {
      ProtectedBookBackupOutcome.Rejected rejected =
          assertInstanceOf(
              ProtectedBookBackupOutcome.Rejected.class,
              accepted(
                  workflow(secretMismatchStore)
                      .backupBook(
                          access(credential), backupPath(), backupKeyPath(), BACKUP_ID, session)));
      assertInstanceOf(
          dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection
              .BackupDestinationAlreadyExists.class,
          rejected.rejection());
    }
  }

  private void backup(
      AttestationMaintenanceTestSupport.Store store,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookBackupOutcome.BackedUp.class,
          accepted(
              workflow(store)
                  .backupBook(
                      access(credential), backupPath(), backupKeyPath(), BACKUP_ID, session)));
    }
  }

  private void restore(
      AttestationMaintenanceTestSupport.Store store,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookRestoreOutcome.Restored.class,
          accepted(
              workflow(store)
                  .restoreBook(
                      restoredBookPath(),
                      restoredKeyPath(),
                      backupPath(),
                      backupKeyPath(),
                      session)));
    }
  }

  private void rekey(
      AttestationMaintenanceTestSupport.Store store,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    try (var session = credential.openSession()) {
      assertInstanceOf(
          ProtectedBookRekeyOutcome.Rekeyed.class,
          accepted(workflow(store).rekeyBook(access(credential), rekeyPath(), session)));
    }
  }

  private AttestedProtectedBookLifecycleWorkflow workflow(
      AttestationMaintenanceTestSupport.Store store) {
    return new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
  }

  private AttestationMaintenanceTestSupport.Store store(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return new AttestationMaintenanceTestSupport.Store(
        bookPath(), List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
  }

  private ProtectedBookAccess access(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return ProtectedBookAccess.fromPublished(
        AttestationMaintenanceTestSupport.bookAccess(bookPath(), credential));
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  private ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete incomplete(
      Path candidateArtifactPath) {
    return new ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete(
        candidateArtifactPath, PublicationTransactionTestFixtures.incompleteResult());
  }

  private ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked blocked(
      Path bookTargetPath, Path secretTargetPath) {
    return new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
        bookTargetPath,
        ProtectedBookPairPublicationMemberState.UNESTABLISHED,
        secretTargetPath,
        ProtectedBookPairPublicationMemberState.UNESTABLISHED);
  }

  private ProtectedBookPairPublicationAdmission.ExistingCompleteBackup existingBackup() {
    return new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
        backupPath(), backupKeyPath());
  }

  private ProtectedBookPairPublication pairPublication(Path bookTargetPath, Path secretTargetPath) {
    return new ProtectedBookPairPublication(
        PublicationTransactionTestFixtures.completedArtifact(bookTargetPath),
        PublicationTransactionTestFixtures.completedArtifact(secretTargetPath));
  }

  private Path bookPath() {
    return temporaryDirectory.resolve("live/book.sqlite");
  }

  private Path backupPath() {
    return temporaryDirectory.resolve("retained/book.fgba");
  }

  private Path backupKeyPath() {
    return temporaryDirectory.resolve("retained/book.key");
  }

  private Path restoredBookPath() {
    return temporaryDirectory.resolve("restored/book.sqlite");
  }

  private Path restoredKeyPath() {
    return temporaryDirectory.resolve("restored/book.key");
  }

  private Path rekeyPath() {
    return temporaryDirectory.resolve("rekeyed/book.key");
  }

  private static void assertIncomplete(ThrowingRunnable invocation) {
    ContractFailureException failure =
        assertThrows(ContractFailureException.class, invocation::run);
    assertEquals(
        ContractErrors.Descriptor.PUBLICATION_TRANSACTION_INCOMPLETE,
        failure.failure().descriptor());
  }

  private static void assertEvidenceBlocked(ThrowingRunnable invocation) {
    ContractFailureException failure =
        assertThrows(ContractFailureException.class, invocation::run);
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED,
        failure.failure().descriptor());
  }

  private static <T> T accepted(MaintenanceDecision<T> decision) {
    return decision.fold(
        value -> value,
        failure -> {
          throw new AssertionError(failure.message());
        });
  }

  /** Supplies an assertion invocation that may perform file I/O. */
  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws IOException;
  }
}
