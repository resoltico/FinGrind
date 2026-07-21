package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationCredentialSource;
import dev.erst.fingrind.core.attestation.AttestationSigningSession;
import dev.erst.fingrind.executor.maintenance.AttestedProtectedBookLifecycleWorkflow;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.ProtectedBookAccess;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/** Published contract adapter for attested protected-book lifecycle operations. */
public final class ProtectedBookMaintenanceService {
  private final AttestedProtectedBookLifecycleWorkflow workflow;

  /** Creates the lifecycle service over the mandatory attested storage boundary. */
  public ProtectedBookMaintenanceService(Clock clock, ProtectedBookMaintenanceStore store) {
    this.workflow =
        new AttestedProtectedBookLifecycleWorkflow(
            Objects.requireNonNull(clock, "clock"), Objects.requireNonNull(store, "store"));
  }

  /**
   * Exports one manifest-attested, independently restorable backup artifact and its key artifact.
   */
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess, Path backupFilePath, Path backupBookKeyFilePath, UUID backupId) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    return withBookSigningSession(
        checkedBookAccess,
        session ->
            toPublishedBackup(
                workflow.backupBook(
                    ProtectedBookAccess.fromPublished(checkedBookAccess),
                    backupFilePath,
                    backupBookKeyFilePath,
                    Objects.requireNonNull(backupId, "backupId"),
                    session)));
  }

  /**
   * Restores a manifest-attested artifact onto a missing target as one signed derived continuation.
   */
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath,
      Path newBookKeyFilePath,
      Path backupArtifactPath,
      Path backupKeyFilePath,
      List<AttestationCredentialSource> attestationCredentialSources) {
    Path checkedBookPath = Objects.requireNonNull(bookFilePath, "bookFilePath");
    return withSigningSession(
        attestationCredentialSources,
        checkedBookPath,
        session ->
            toPublishedRestore(
                workflow.restoreBook(
                    checkedBookPath,
                    newBookKeyFilePath,
                    backupArtifactPath,
                    backupKeyFilePath,
                    session)));
  }

  /** Rekeys one book only after its exact rekey operation is appended to the staged copy. */
  public ContractDecision<RekeyBookResult> rekeyBook(
      BookAccess bookAccess, Path newBookKeyFilePath) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    return withBookSigningSession(
        checkedBookAccess,
        session ->
            toPublishedRekey(
                workflow.rekeyBook(
                    ProtectedBookAccess.fromPublished(checkedBookAccess),
                    newBookKeyFilePath,
                    session)));
  }

  private static <T> ContractDecision<T> withSigningSession(
      List<AttestationCredentialSource> credentialSources,
      Path contextPath,
      Function<AttestationSigningSession, ContractDecision<T>> action) {
    Path checkedContextPath = Objects.requireNonNull(contextPath, "contextPath");
    Function<AttestationSigningSession, ContractDecision<T>> checkedAction =
        Objects.requireNonNull(action, "action");
    AttestationSigningSession session;
    try {
      session =
          AttestationSigningSessionFactory.open(
              List.copyOf(Objects.requireNonNull(credentialSources, "credentialSources")));
    } catch (AttestationCredentialException
        | IllegalArgumentException
        | NullPointerException exception) {
      return invalidAttestationCredentials(checkedContextPath);
    }
    try (session) {
      return checkedAction.apply(session);
    }
  }

  private static <T> ContractDecision<T> withBookSigningSession(
      BookAccess bookAccess, Function<AttestationSigningSession, ContractDecision<T>> action) {
    BookAccess checkedBookAccess = Objects.requireNonNull(bookAccess, "bookAccess");
    List<AttestationCredentialSource> credentialSources;
    try {
      credentialSources = checkedBookAccess.requireAttestationCredentialSources();
    } catch (IllegalStateException exception) {
      return invalidAttestationCredentials(checkedBookAccess.bookFilePath());
    }
    return withSigningSession(credentialSources, checkedBookAccess.bookFilePath(), action);
  }

  private static <T> ContractDecision<T> invalidAttestationCredentials(Path contextPath) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
            contextPath,
            "FinGrind could not open the selected attestation credentials.",
            "Provide one through five readable existing attestation credential triples authorized for this operation.",
            "--attestation-principal-id"));
  }

  private static ContractDecision<BackupBookResult> toPublishedBackup(
      MaintenanceDecision<dev.erst.fingrind.executor.maintenance.ProtectedBookBackupOutcome>
          decision) {
    return decision.fold(
        outcome ->
            ContractDecision.accepted(
                ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
        failure -> ContractDecision.rejected(failure.toContractFailure()));
  }

  private static ContractDecision<RestoreBookResult> toPublishedRestore(
      MaintenanceDecision<dev.erst.fingrind.executor.maintenance.ProtectedBookRestoreOutcome>
          decision) {
    return decision.fold(
        outcome ->
            ContractDecision.accepted(
                ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
        failure -> ContractDecision.rejected(failure.toContractFailure()));
  }

  private static ContractDecision<RekeyBookResult> toPublishedRekey(
      MaintenanceDecision<dev.erst.fingrind.executor.maintenance.ProtectedBookRekeyOutcome>
          decision) {
    return decision.fold(
        outcome ->
            ContractDecision.accepted(
                ProtectedBookMaintenancePublishedLanguageTranslator.toPublished(outcome)),
        failure -> ContractDecision.rejected(failure.toContractFailure()));
  }
}
