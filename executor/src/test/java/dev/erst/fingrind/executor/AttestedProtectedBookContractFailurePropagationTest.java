package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationCapability;
import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome;
import dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome;
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

/** Verifies that deterministic contract failures cross every maintenance boundary unchanged. */
class AttestedProtectedBookContractFailurePropagationTest {
  private static final Instant RECORDED_AT = Instant.parse("2026-07-23T00:00:00Z");
  private static final Clock CLOCK = Clock.fixed(RECORDED_AT, ZoneOffset.UTC);
  private static final UUID BACKUP_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");

  @TempDir Path temporaryDirectory;

  @BeforeEach
  void canonicalizeTemporaryDirectory() throws IOException {
    temporaryDirectory = temporaryDirectory.toRealPath();
  }

  @Test
  void backup_rethrowsContractFailuresFromAtomicPairPublicationAdmission() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store preparationStore = store(credential);
    ContractFailureException preparationFailure = contractFailure("backup publication fault");
    preparationStore.setPairAdmissionFailure(preparationFailure);

    assertSame(
        preparationFailure,
        assertThrows(
            ContractFailureException.class, () -> backup(preparationStore, access, credential)));
  }

  @Test
  void backup_rethrowsContractFailuresFromAcknowledgementAndArtifactVerification()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store acknowledgementStore = store(credential);
    ContractFailureException acknowledgementFailure =
        contractFailure("backup acknowledgement fault");
    acknowledgementStore.overrides().appendFailure(acknowledgementFailure);

    assertSame(
        acknowledgementFailure,
        assertThrows(
            ContractFailureException.class,
            () -> backup(acknowledgementStore, access, credential)));

    AttestationMaintenanceTestSupport.Store resumedStore = store(credential);
    accepted(backup(resumedStore, access, credential));
    ContractFailureException artifactVerificationFailure =
        contractFailure("backup artifact verification fault");
    resumedStore.overrides().backupArtifactVerificationFailure(artifactVerificationFailure);

    assertSame(
        artifactVerificationFailure,
        assertThrows(
            ContractFailureException.class, () -> backup(resumedStore, access, credential)));
  }

  @Test
  void restore_rethrowsContractFailuresFromSourceLeaseAndPublicationPreparation()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();

    AttestationMaintenanceTestSupport.Store leaseStore = store(credential);
    ContractFailureException leaseFailure = contractFailure("restore source lease fault");
    leaseStore.overrides().workflowScopeAcquisitionFailure(leaseFailure);

    assertSame(
        leaseFailure,
        assertThrows(ContractFailureException.class, () -> restore(leaseStore, credential)));

    AttestationMaintenanceTestSupport.Store preparationStore = store(credential);
    assertInstanceOf(
        ProtectedBookBackupOutcome.BackedUp.class,
        accepted(backup(preparationStore, access(credential), credential)));
    ContractFailureException preparationFailure = contractFailure("restore publication fault");
    preparationStore.setPairAdmissionFailure(preparationFailure);

    assertSame(
        preparationFailure,
        assertThrows(ContractFailureException.class, () -> restore(preparationStore, credential)));
  }

  @Test
  void rekeyAndRegistryMutation_rethrowContractFailuresFromTheirMutationBoundaries()
      throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);

    AttestationMaintenanceTestSupport.Store rekeyPreparationStore = store(credential);
    ContractFailureException rekeyPreparationFailure = contractFailure("rekey publication fault");
    rekeyPreparationStore.setPairAdmissionFailure(rekeyPreparationFailure);

    assertSame(
        rekeyPreparationFailure,
        assertThrows(
            ContractFailureException.class,
            () -> rekey(rekeyPreparationStore, access, credential)));

    AttestationMaintenanceTestSupport.Store rekeyAppendStore = store(credential);
    ContractFailureException rekeyAppendFailure = contractFailure("rekey append fault");
    rekeyAppendStore.overrides().appendFailure(rekeyAppendFailure);

    assertSame(
        rekeyAppendFailure,
        assertThrows(
            ContractFailureException.class, () -> rekey(rekeyAppendStore, access, credential)));

    AttestationMaintenanceTestSupport.Store registryStore = store(credential);
    ContractFailureException registryAppendFailure = contractFailure("registry append fault");
    registryStore.overrides().appendFailure(registryAppendFailure);

    assertSame(
        registryAppendFailure,
        assertThrows(
            ContractFailureException.class,
            () -> mutateRegistry(registryStore, access, credential)));
  }

  @Test
  void rekey_mapsAnUnexpectedAppendFailureToTheLocalMaintenanceFailure() throws IOException {
    AttestationMaintenanceTestSupport.CredentialFixture credential = credential();
    ProtectedBookAccess access = access(credential);
    AttestationMaintenanceTestSupport.Store store = store(credential);
    store.overrides().appendFailure(new IllegalStateException("rekey storage fault"));

    assertEquals(
        "Failed to stage the attested rekey operation.",
        rekey(store, access, credential)
            .fold(
                ignored -> {
                  throw new AssertionError("Unexpected successful rekey.");
                },
                failure -> failure.message()));
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
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    try (var session = credential.openSession()) {
      return workflow(store)
          .restoreBook(
              restoredBookPath(), restoredKeyPath(), backupPath(), backupKeyPath(), session);
    }
  }

  private MaintenanceDecision<ProtectedBookRekeyOutcome> rekey(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    try (var session = credential.openSession()) {
      return workflow(store).rekeyBook(access, rekeyKeyPath(), session);
    }
  }

  private void mutateRegistry(
      AttestationMaintenanceTestSupport.Store store,
      ProtectedBookAccess access,
      AttestationMaintenanceTestSupport.CredentialFixture credential)
      throws IOException {
    try (var session = credential.openSession()) {
      workflow(store).mutateRegistry(access, policyMutation(), session);
    }
  }

  private ContractFailureException contractFailure(String message) {
    return new ContractFailureException(
        ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE.failureAt(
            bookPath(), message, "Repair the storage boundary before retrying.", "--book-file"));
  }

  private AttestedProtectedBookLifecycleWorkflow workflow(
      AttestationMaintenanceTestSupport.Store store) {
    return new AttestedProtectedBookLifecycleWorkflow(CLOCK, store);
  }

  private AttestationMaintenanceTestSupport.Store store(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    AttestationMaintenanceTestSupport.Store store =
        new AttestationMaintenanceTestSupport.Store(
            bookPath(),
            List.of(AttestationMaintenanceTestSupport.genesis(credential, RECORDED_AT)));
    return store;
  }

  private ProtectedBookAccess access(
      AttestationMaintenanceTestSupport.CredentialFixture credential) {
    return ProtectedBookAccess.fromPublished(
        AttestationMaintenanceTestSupport.bookAccess(bookPath(), credential));
  }

  private AttestationMaintenanceTestSupport.CredentialFixture credential() throws IOException {
    return AttestationMaintenanceTestSupport.createCredential(temporaryDirectory);
  }

  private static AttestationRegistryMutation.AlterPolicy policyMutation() {
    return new AttestationRegistryMutation.AlterPolicy(
        List.of(new AttestationRegistryMutation.PolicyRule(AttestationCapability.CLOSE_PERIOD, 1)),
        List.of(),
        List.of());
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

  private Path rekeyKeyPath() {
    return temporaryDirectory.resolve("rekeyed/book.key");
  }

  private static <T> T accepted(MaintenanceDecision<T> decision) {
    return decision.fold(
        value -> value,
        failure -> {
          throw new AssertionError(failure.message());
        });
  }
}
