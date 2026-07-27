package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationMemberState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRecoveryRecordState;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationRetention;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
import dev.erst.fingrind.core.attestation.AttestationBackupAcknowledgement;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationFailureOutcome;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationSourceIdentity;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Covers exact pair-recovery alternatives for backup, restore, and rekey workflow entry points. */
class AttestedProtectedBookPairRecoveryCoverageTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-25T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void backupRecoveryClassifiesEveryRetainedPairAlternative() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store recovered = backedUpStore(credential, access);
    recovered.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            new ProtectedBookPairPublicationBinding.Backup(bookPath(), acknowledgement()),
            retention(backupPath(), backupKeyPath())));
    ProtectedBookBackupOutcome.BackedUp recoveredOutcome =
        assertInstanceOf(
            ProtectedBookBackupOutcome.BackedUp.class,
            accepted(backup(recovered, access, credential)));
    assertEquals(
        ProtectedBookPairPublicationCompletion.RECOVERED,
        recoveredOutcome.pairPublicationCompletion());

    AttestationMaintenanceTestSupport.Store existing = store(credential);
    existing.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
            backupPath().resolveSibling("other.fgba"), backupKeyPath()));
    assertBackupRejection(existing, access, credential);

    AttestationMaintenanceTestSupport.Store existingWithOtherKey = store(credential);
    existingWithOtherKey.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
            backupPath(), backupKeyPath().resolveSibling("other.key")));
    assertBackupRejection(existingWithOtherKey, access, credential);

    assertPreservedBackup(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
            backupPath(),
            backupKeyPath(),
            ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
            retention(backupPath(), backupKeyPath())),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN);
    assertPreservedBackup(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
            backupPath(),
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            backupKeyPath(),
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            null),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED);
    assertPreservedBackup(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
            backupPath(),
            ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
            backupKeyPath(),
            ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
            null),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN);

    AttestationMaintenanceTestSupport.Store mismatched = backedUpStore(credential, access);
    mismatched.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            new ProtectedBookPairPublicationBinding.Rekey(
                sourceIdentity(access), commit(), commit()),
            retention(backupPath(), backupKeyPath())));
    assertInstanceOf(MaintenanceDecision.Failed.class, backup(mismatched, access, credential));

    AttestationMaintenanceTestSupport.Store mismatchedBackupId = backedUpStore(credential, access);
    mismatchedBackupId.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            new ProtectedBookPairPublicationBinding.Backup(
                bookPath(),
                new AttestationBackupAcknowledgement(
                    UUID.fromString("018f0000-0000-7000-8000-000000000002"),
                    new byte[32],
                    BigInteger.ONE,
                    new byte[32])),
            retention(backupPath(), backupKeyPath())));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, backup(mismatchedBackupId, access, credential));
  }

  @Test
  void restoreRecoveryClassifiesEveryRetainedPairAlternative() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);
    Path restoredBook = restoredBookPath();
    Path restoredKey = restoredKeyPath();

    AttestationMaintenanceTestSupport.Store recovered = backedUpStore(credential, access);
    recovered.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            new ProtectedBookPairPublicationBinding.Restore(
                backupPath(), backupKeyPath(), acknowledgement(), commit()),
            retention(restoredBook, restoredKey)));
    ProtectedBookRestoreOutcome.Restored recoveredOutcome =
        assertInstanceOf(
            ProtectedBookRestoreOutcome.Restored.class,
            accepted(restore(recovered, credential, restoredBook, restoredKey)));
    assertEquals(
        ProtectedBookPairPublicationCompletion.RECOVERED,
        recoveredOutcome.pairPublicationCompletion());

    AttestationMaintenanceTestSupport.Store existing = backedUpStore(credential, access);
    existing.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
            backupPath(), backupKeyPath()));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, restore(existing, credential, restoredBook, restoredKey));

    assertPreservedRestore(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
            restoredBook,
            restoredKey,
            ProtectedBookPairPublicationRecoveryRecordState.DURABILITY_UNCONFIRMED,
            retention(restoredBook, restoredKey)),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN);
    assertPreservedRestore(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
            restoredBook,
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            restoredKey,
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            null),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED);
    assertPreservedRestore(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
            restoredBook,
            ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
            restoredKey,
            ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
            null),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN);

    AttestationMaintenanceTestSupport.Store mismatched = backedUpStore(credential, access);
    mismatched.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            new ProtectedBookPairPublicationBinding.Backup(bookPath(), acknowledgement()),
            retention(restoredBook, restoredKey)));
    assertInstanceOf(
        MaintenanceDecision.Failed.class,
        restore(mismatched, credential, restoredBook, restoredKey));
  }

  @Test
  void rekeyRecoveryClassifiesEveryRetainedPairAlternative() throws Exception {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);
    Path newKey = rekeyPath();

    AttestationMaintenanceTestSupport.Store recovered = store(credential);
    recovered.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            new ProtectedBookPairPublicationBinding.Rekey(
                sourceIdentity(access), commit(), commit()),
            retention(bookPath(), newKey)));
    ProtectedBookRekeyOutcome.Rekeyed recoveredOutcome =
        assertInstanceOf(
            ProtectedBookRekeyOutcome.Rekeyed.class,
            accepted(rekey(recovered, access, credential, newKey)));
    assertEquals(
        ProtectedBookPairPublicationCompletion.RECOVERED,
        recoveredOutcome.pairPublicationCompletion());

    AttestationMaintenanceTestSupport.Store existing = store(credential);
    existing.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(bookPath(), newKey));
    assertInstanceOf(MaintenanceDecision.Failed.class, rekey(existing, access, credential, newKey));

    assertPreservedRekey(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.PrepublicationRecoveryRequired(
            bookPath(),
            newKey,
            ProtectedBookPairPublicationRecoveryRecordState.DURABLY_RETAINED,
            retention(bookPath(), newKey)),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN);
    assertPreservedRekey(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.EvidenceBlocked(
            bookPath(),
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            newKey,
            ProtectedBookPairPublicationMemberState.UNESTABLISHED,
            null),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_EVIDENCE_BLOCKED);
    assertPreservedRekey(
        credential,
        access,
        new ProtectedBookPairPublicationFailureOutcome.CompletionUncertain(
            bookPath(),
            ProtectedBookPairPublicationMemberState.OUTCOME_UNCERTAIN,
            newKey,
            ProtectedBookPairPublicationMemberState.NOT_ATTEMPTED,
            null),
        ContractErrors.Descriptor.PROTECTED_BOOK_PAIR_PUBLICATION_UNCERTAIN);

    AttestationMaintenanceTestSupport.Store mismatched = store(credential);
    mismatched.setInjectedPairAdmission(
        new ProtectedBookPairPublicationAdmission.Recovered(
            new ProtectedBookPairPublicationBinding.Backup(bookPath(), acknowledgement()),
            retention(bookPath(), newKey)));
    assertInstanceOf(
        MaintenanceDecision.Failed.class, rekey(mismatched, access, credential, newKey));
  }

  private void assertPreservedBackup(
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      ProtectedBookAccess access,
      ProtectedBookPairPublicationAdmission admission,
      ContractErrors.Descriptor descriptor) {
    AttestationMaintenanceTestSupport.Store store = store(credential);
    store.setInjectedPairAdmission(admission);
    assertPreserved(descriptor, () -> backup(store, access, credential));
  }

  private void assertPreservedRestore(
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      ProtectedBookAccess access,
      ProtectedBookPairPublicationAdmission admission,
      ContractErrors.Descriptor descriptor)
      throws IOException {
    AttestationMaintenanceTestSupport.Store store = backedUpStore(credential, access);
    store.setInjectedPairAdmission(admission);
    assertPreserved(
        descriptor, () -> restore(store, credential, restoredBookPath(), restoredKeyPath()));
  }

  private void assertPreservedRekey(
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      ProtectedBookAccess access,
      ProtectedBookPairPublicationAdmission admission,
      ContractErrors.Descriptor descriptor) {
    AttestationMaintenanceTestSupport.Store store = store(credential);
    store.setInjectedPairAdmission(admission);
    assertPreserved(descriptor, () -> rekey(store, access, credential, rekeyPath()));
  }

  private static void assertPreserved(
      ContractErrors.Descriptor expectedDescriptor, ThrowingRunnable action) {
    ContractFailureException failure = assertThrows(ContractFailureException.class, action::run);
    assertEquals(expectedDescriptor, failure.failure().descriptor());
  }

  private void assertBackupRejection(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    ProtectedBookBackupOutcome.Rejected rejected =
        assertInstanceOf(
            ProtectedBookBackupOutcome.Rejected.class, accepted(backup(store, access, credential)));
    assertInstanceOf(
        ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists.class,
        rejected.rejection());
  }

  private MaintenanceDecision<ProtectedBookBackupOutcome> backup(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    try (var session = credential.openSession()) {
      return workflow(store).backupBook(access, backupPath(), backupKeyPath(), BACKUP_ID, session);
    }
  }

  private MaintenanceDecision<ProtectedBookRestoreOutcome> restore(
      AttestationMaintenanceTestSupport.Store store,
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      Path restoredBook,
      Path restoredKey)
      throws IOException {
    try (var session = credential.openSession()) {
      return workflow(store)
          .restoreBook(restoredBook, restoredKey, backupPath(), backupKeyPath(), session);
    }
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> rekey(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential,
      Path newKey)
      throws IOException {
    try (var session = credential.openSession()) {
      return workflow(store).rekeyBook(access, newKey, session);
    }
  }

  private AttestationMaintenanceTestSupport.Store backedUpStore(
      AttestationMaintenanceTestSupport.CredentialFixture credential, ProtectedBookAccess access)
      throws IOException {
    AttestationMaintenanceTestSupport.Store store = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class, accepted(backup(store, access, credential)));
    return store;
  }

  private AttestationMaintenanceTestSupport.Store store(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return new AttestationMaintenanceTestSupport.Store(
        bookPath(), List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
  }

  private AttestedProtectedBookLifecycleWorkflow workflow(
      AttestationMaintenanceTestSupport.Store store) {
    return new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
  }

  private ProtectedBookAccess access(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return ProtectedBookAccess.fromPublished(
        AttestationMaintenanceTestSupport.bookAccess(bookPath(), credential));
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  private static <T> T accepted(MaintenanceDecision<T> decision) {
    return decision.fold(
        value -> value,
        failure -> {
          throw new AssertionError(failure.message());
        });
  }

  private static AttestationBackupAcknowledgement acknowledgement() {
    return new AttestationBackupAcknowledgement(
        BACKUP_ID, new byte[32], BigInteger.ZERO, new byte[32]);
  }

  private static AttestationCommit commit() {
    return new AttestationCommit(BigInteger.ONE, "a".repeat(64));
  }

  private static ProtectedBookPairPublicationSourceIdentity sourceIdentity(
      ProtectedBookAccess access) {
    return ProtectedBookPairPublicationSourceIdentity.from(access);
  }

  private static ProtectedBookPairPublicationRetention retention(Path book, Path key) {
    return new ProtectedBookPairPublicationRetention(
        publication(book, ".book-stage"), publication(key, ".key-stage"));
  }

  private static ArtifactPublicationResult publication(Path published, String stageName) {
    Path normalizedPublished = published.toAbsolutePath().normalize();
    return ArtifactPublicationResult.restoreCapturedCanonicalPaths(
        normalizedPublished,
        new ArtifactPublicationRetention(normalizedPublished.resolveSibling(stageName)));
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

  /** Executes one checked maintenance operation whose failure is asserted by the test. */
  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws IOException;
  }
}
