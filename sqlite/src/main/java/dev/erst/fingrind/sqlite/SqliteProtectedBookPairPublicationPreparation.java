package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PublicationTransactionOwnerContext;
import dev.erst.fingrind.core.PublicationTransactionPublisher;
import dev.erst.fingrind.core.PublicationTransactionRecoveryReceipt;
import dev.erst.fingrind.core.PublicationTransactionService;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Coordinates leases, transaction recovery discovery, and first-publication target preparation. */
final class SqliteProtectedBookPairPublicationPreparation {
  /** Opens the canonical journal service only after the exact target pair is already leased. */
  @FunctionalInterface
  interface PublicationTransactionServiceFactory {
    /** Opens the transaction service that owns the selected pair's private stages. */
    PublicationTransactionService open() throws IOException;
  }

  private final SqliteProtectedBookMaintenanceArtifactStore artifactStore;
  private final PublicationTransactionServiceFactory publicationTransactions;

  private SqliteProtectedBookPairPublicationPreparation(
      SqliteProtectedBookMaintenanceArtifactStore artifactStore,
      PublicationTransactionServiceFactory publicationTransactions) {
    this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
    this.publicationTransactions =
        Objects.requireNonNull(publicationTransactions, "publicationTransactions");
  }

  /** Builds a production preparation boundary without retaining legacy recovery authority. */
  static SqliteProtectedBookPairPublicationPreparation journaled(
      SqliteProtectedBookMaintenanceArtifactStore artifactStore) {
    return new SqliteProtectedBookPairPublicationPreparation(
        artifactStore, PublicationTransactionPublisher::openCanonical);
  }

  /** Creates an isolated journal-only admission boundary for package-local integration tests. */
  static SqliteProtectedBookPairPublicationPreparation journaledForTesting(
      SqliteProtectedBookMaintenanceArtifactStore artifactStore,
      PublicationTransactionServiceFactory publicationTransactions) {
    return new SqliteProtectedBookPairPublicationPreparation(
        artifactStore, publicationTransactions);
  }

  /**
   * Atomically inspects legacy evidence and reserves a journal-owned pair only when no evidence
   * remains.
   */
  ProtectedBookPairPublicationAdmission admit(
      Path normalizedBookTargetPath,
      Path normalizedSecretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookPairPublicationRecoveryRequest request,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      SqliteTargetAdmissionLeases targetAdmissionLeases) {
    RestoredBookTargetPolicy checkedPolicy =
        Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
    ProtectedBookPairPublicationRecoveryRequest checkedRequest =
        Objects.requireNonNull(request, "request");
    ProtectedBookMaintenanceArtifactRole checkedBookRole =
        Objects.requireNonNull(bookArtifactRole, "bookArtifactRole");
    ProtectedBookMaintenanceArtifactRole checkedSecretRole =
        Objects.requireNonNull(secretArtifactRole, "secretArtifactRole");
    SqliteTargetAdmissionLeases checkedTargetAdmissionLeases =
        Objects.requireNonNull(targetAdmissionLeases, "targetAdmissionLeases");
    Path bookTargetPath =
        artifactStore.normalizeFinalTarget(
            Objects.requireNonNull(normalizedBookTargetPath, "normalizedBookTargetPath"),
            "bookTargetPath",
            checkedBookRole);
    Path secretTargetPath =
        artifactStore.normalizeFinalTarget(
            Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath"),
            "secretTargetPath",
            checkedSecretRole);
    SqliteProtectedBookPairTargetSecurity.requirePrepublicationPairTargetAdmission(
        bookTargetPath, secretTargetPath, checkedBookRole, checkedSecretRole);
    try (SqlitePairPublicationPreparationResources resources =
        new SqlitePairPublicationPreparationResources()) {
      checkedTargetAdmissionLeases.transferTo(resources);
      PublicationTransactionOwnerContext ownerContext =
          SqliteProtectedBookPublicationOwnerContext.forPair(
              checkedRequest, bookTargetPath, secretTargetPath, checkedPolicy);
      try {
        PublicationTransactionService transactions = publicationTransactions.open();
        Optional<PublicationTransactionRecoveryReceipt> recovered =
            transactions.recoverMatchingOwnerContext(ownerContext);
        if (recovered.isPresent()) {
          PublicationTransactionRecoveryReceipt receipt = recovered.orElseThrow();
          if (!receipt.transactionResult().successful()) {
            return new ProtectedBookPairPublicationAdmission.PublicationTransactionIncomplete(
                bookTargetPath, receipt.transactionResult());
          }
          return new ProtectedBookPairPublicationAdmission.Recovered(
              Objects.requireNonNull(
                  SqlitePublicationTransactionPair.recoverCompleted(
                      receipt, bookTargetPath, secretTargetPath),
                  "A successful journal recovery must prove the protected-book publication pair."));
        }
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to recover the FinGrind protected-book publication transaction.", exception);
      }
      if (!SqliteProtectedBookPairPublicationEvidenceScanner.hasLegacyResidue(
          bookTargetPath, secretTargetPath)) {
        SqlitePairPublicationReconciliation reconciliation =
            SqliteJournaledPairAdmissionClassification.classifyCleanTargets(
                bookTargetPath, secretTargetPath, checkedPolicy, checkedRequest);
        return switch (reconciliation) {
          case SqlitePairPublicationReconciliationExistingCompleteBackup existingCompleteBackup ->
              new ProtectedBookPairPublicationAdmission.ExistingCompleteBackup(
                  existingCompleteBackup.backupArtifactPath(),
                  existingCompleteBackup.backupKeyPath());
          case SqlitePairPublicationReconciliationEvidenceBlocked blocked ->
              SqliteJournaledPairAdmissionClassification.evidenceBlocked(
                  blocked.bookArtifactPath(), blocked.secretArtifactPath());
          case SqlitePairPublicationReconciliationAbsent _ -> {
            try {
              yield new ProtectedBookPairPublicationAdmission.Prepared(
                  SqliteProtectedBookPairPublicationTargets.prepareJournaledWithHeldLeases(
                      resources,
                      secretTargetPath,
                      bookTargetPath,
                      checkedPolicy,
                      checkedBookRole,
                      checkedSecretRole,
                      checkedRequest,
                      publicationTransactions.open()));
            } catch (IOException exception) {
              throw new IllegalStateException(
                  "Failed to open the FinGrind protected-book publication transaction service.",
                  exception);
            }
          }
        };
      }
      return SqliteJournaledPairAdmissionClassification.evidenceBlocked(
          bookTargetPath, secretTargetPath);
    }
  }
}
