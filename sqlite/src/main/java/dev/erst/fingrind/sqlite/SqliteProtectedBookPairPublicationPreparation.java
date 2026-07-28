package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationAdmission;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationBinding;
import dev.erst.fingrind.executor.spi.ProtectedBookPairPublicationRecoveryRequest;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Coordinates leases, recovery classification, and first-publication target preparation. */
final class SqliteProtectedBookPairPublicationPreparation {
  /** Verifies a fully materialized record-bound pair without consulting a prior rekey key. */
  @FunctionalInterface
  interface RecoveredPairVerifier {
    /** Returns whether the final pair is cryptographically valid for its immutable operation. */
    boolean verifies(
        Path normalizedBookTargetPath,
        Path normalizedSecretTargetPath,
        ProtectedBookPairPublicationBinding binding);
  }

  /** Reconciles durable pair-publication evidence before any new target is reserved. */
  @FunctionalInterface
  interface PairPublicationRecovery {
    SqlitePairPublicationReconciliation reconcile(
        Path bookTargetPath,
        Path secretTargetPath,
        RestoredBookTargetPolicy expectedBookTargetPolicy,
        ProtectedBookPairPublicationRecoveryRequest request,
        ProtectedBookMaintenanceArtifactRole bookArtifactRole,
        ProtectedBookMaintenanceArtifactRole secretArtifactRole);
  }

  /** Performs one generated-secret preflight action that can fail with filesystem I/O. */
  @FunctionalInterface
  interface GeneratedSecretTargetPreparation {
    /** Prepares the supplied normalized generated-secret target. */
    void prepare(Path normalizedSecretTargetPath) throws IOException;
  }

  /** Creates one exclusive reservation for one final protected-book artifact destination. */
  @FunctionalInterface
  interface DestinationReservationCreator {
    /**
     * Reserves the supplied normalized destination until publication or non-destructive release.
     */
    SqliteOwnedDestinationReservation reserve(Path normalizedTargetPath) throws IOException;
  }

  private final SqliteProtectedBookMaintenanceArtifactStore artifactStore;
  private final PairPublicationRecovery recovery;

  SqliteProtectedBookPairPublicationPreparation(
      SqliteProtectedBookMaintenanceArtifactStore artifactStore,
      RecoveredPairVerifier recoveredPairVerifier,
      SqliteProtectedBookPublicationSupport.PairDirectoryForcer directoryForcer,
      SqliteProtectedBookPairPublicationRecord.RecoveryRecordFileForcer recoveryRecordFileForcer) {
    this(
        artifactStore,
        new SqliteProtectedBookPairPublicationRecovery(
            recoveredPairVerifier, directoryForcer, recoveryRecordFileForcer));
  }

  SqliteProtectedBookPairPublicationPreparation(
      SqliteProtectedBookMaintenanceArtifactStore artifactStore, PairPublicationRecovery recovery) {
    this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
    this.recovery = Objects.requireNonNull(recovery, "recovery");
  }

  /**
   * Atomically reconciles durable evidence and reserves a new pair only when no evidence remains.
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
    SqliteProtectedBookPairPublicationTargets.requirePrepublicationPairTargetAdmission(
        bookTargetPath, secretTargetPath, checkedBookRole, checkedSecretRole);
    try (SqlitePairPublicationPreparationResources resources =
        new SqlitePairPublicationPreparationResources()) {
      checkedTargetAdmissionLeases.transferTo(resources);
      SqlitePairPublicationReconciliation reconciliation;
      try {
        reconciliation =
            recovery.reconcile(
                bookTargetPath,
                secretTargetPath,
                checkedPolicy,
                checkedRequest,
                checkedBookRole,
                checkedSecretRole);
      } catch (SqliteCallerPathContractException exception) {
        throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
            SqlitePairPublicationAdmissionMapper.roleForRecoveryPathFailure(
                exception, secretTargetPath, checkedBookRole, checkedSecretRole),
            exception);
      }
      if (reconciliation instanceof SqlitePairPublicationReconciliationAbsent) {
        return new ProtectedBookPairPublicationAdmission.Prepared(
            SqliteProtectedBookPairPublicationTargets.prepareWithHeldLeases(
                resources,
                secretTargetPath,
                bookTargetPath,
                checkedPolicy,
                checkedBookRole,
                checkedSecretRole));
      }
      return SqlitePairPublicationAdmissionMapper.fromRecoveredReconciliation(reconciliation);
    }
  }

  static void prepareGeneratedSecretTarget(
      Path normalizedSecretTargetPath, GeneratedSecretTargetPreparation preparation) {
    SqliteProtectedBookPairPublicationTargets.prepareGeneratedSecretTarget(
        normalizedSecretTargetPath, preparation);
  }

  static void prepareGeneratedSecretTarget(
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      GeneratedSecretTargetPreparation preparation) {
    SqliteProtectedBookPairPublicationTargets.prepareGeneratedSecretTarget(
        normalizedSecretTargetPath, secretArtifactRole, preparation);
  }

  static SqliteOwnedDestinationReservation reserveAbsentBookTarget(
      Path bookTargetPath, ProtectedBookMaintenanceArtifactRole bookArtifactRole) {
    return SqliteProtectedBookPairPublicationTargets.reserveAbsentBookTarget(
        bookTargetPath, bookArtifactRole);
  }

  static SqliteOwnedDestinationReservation reserveAbsentBookTarget(
      Path bookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      DestinationReservationCreator reservationCreator) {
    return SqliteProtectedBookPairPublicationTargets.reserveAbsentBookTarget(
        bookTargetPath, bookArtifactRole, reservationCreator);
  }

  static SqliteOwnedDestinationReservation reserveAbsentSecretTarget(Path secretTargetPath) {
    return SqliteProtectedBookPairPublicationTargets.reserveAbsentSecretTarget(secretTargetPath);
  }

  static SqliteOwnedDestinationReservation reserveAbsentSecretTarget(
      Path secretTargetPath, DestinationReservationCreator reservationCreator) {
    return SqliteProtectedBookPairPublicationTargets.reserveAbsentSecretTarget(
        secretTargetPath, reservationCreator);
  }

  static ProtectedBookMaintenanceRejectionException secretTargetPathRejection(
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      SqliteCallerPathContractException exception) {
    return SqliteProtectedBookPairPublicationTargets.secretTargetPathRejection(
        secretArtifactRole, exception);
  }

  static ProtectedBookMaintenanceRejection occupiedBookTargetRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole, Path bookTargetPath) {
    return SqliteProtectedBookPairPublicationTargets.occupiedBookTargetRejection(
        artifactRole, bookTargetPath);
  }
}
