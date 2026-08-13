package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationRegistryMutation;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.spi.AttestedProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Public lifecycle facade for independently owned protected-book maintenance workflows. */
public final class AttestedProtectedBookLifecycleWorkflow {
  private final AttestedProtectedBookBackupWorkflow backupWorkflow;
  private final AttestedProtectedBookRestoreWorkflow restoreWorkflow;
  private final AttestedProtectedBookRekeyWorkflow rekeyWorkflow;
  private final AttestedProtectedBookRegistryMutationWorkflow registryMutationWorkflow;

  /** Creates one lifecycle workflow over an attested protected-book storage implementation. */
  public AttestedProtectedBookLifecycleWorkflow(Clock clock, ProtectedBookMaintenanceStore store) {
    Clock checkedClock = Objects.requireNonNull(clock, "clock");
    AttestedProtectedBookMaintenanceStore checkedStore =
        AttestedProtectedBookMaintenanceStore.require(store);
    AttestedProtectedBookPairPublicationRecovery publicationRecovery =
        new AttestedProtectedBookPairPublicationRecovery(checkedStore);
    backupWorkflow = new AttestedProtectedBookBackupWorkflow(checkedClock, checkedStore);
    restoreWorkflow =
        new AttestedProtectedBookRestoreWorkflow(checkedClock, checkedStore, publicationRecovery);
    rekeyWorkflow =
        new AttestedProtectedBookRekeyWorkflow(checkedClock, checkedStore, publicationRecovery);
    registryMutationWorkflow =
        new AttestedProtectedBookRegistryMutationWorkflow(checkedClock, checkedStore);
  }

  /** Stages, verifies, seals, publishes, and acknowledges one manifest-attested backup artifact. */
  public MaintenanceDecision<ProtectedBookBackupOutcome> backupBook(
      ProtectedBookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      UUID backupId,
      AttestationSigningSession signingSession) {
    return backupWorkflow.backupBook(
        bookAccess, backupFilePath, backupBookKeyFilePath, backupId, signingSession);
  }

  /** Restores a manifest-attested artifact as one independently signed derived continuation. */
  public MaintenanceDecision<ProtectedBookRestoreOutcome> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupArtifactPath,
      Path backupKeyFilePath,
      AttestationSigningSession signingSession) {
    return restoreWorkflow.restore(
        bookFilePath, newBookKeyFilePath, backupArtifactPath, backupKeyFilePath, signingSession);
  }

  /** Rekeys one book only after writing its exact signed rekey operation into the staged copy. */
  public MaintenanceDecision<ProtectedBookRekeyOutcome> rekeyBook(
      ProtectedBookAccess bookAccess,
      Path newBookKeyFilePath,
      AttestationSigningSession signingSession) {
    return rekeyWorkflow.rekey(bookAccess, newBookKeyFilePath, signingSession);
  }

  /** Appends one exact credential-registry or authorization-policy mutation to the live book. */
  public MaintenanceDecision<ProtectedBookRegistryMutationOutcome> mutateRegistry(
      ProtectedBookAccess bookAccess,
      AttestationRegistryMutation mutation,
      AttestationSigningSession signingSession) {
    return registryMutationWorkflow.mutate(bookAccess, mutation, signingSession);
  }
}
