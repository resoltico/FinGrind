package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Prepares and recovers the two destinations owned by one protected-book publication. */
final class SqliteProtectedBookPairPublicationPreparation {
  /** Decides whether one owned interrupted pair completed before its process stopped. */
  @FunctionalInterface
  interface InterruptedPairCompanionBookVerifier {
    /** Returns whether the companion book opens with the published generated secret. */
    boolean opens(Path normalizedBookTargetPath, Path normalizedSecretTargetPath);
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
    /** Reserves the supplied normalized destination until publication or cleanup. */
    SqliteOwnedDestinationReservation reserve(Path normalizedTargetPath) throws IOException;
  }

  private final SqliteProtectedBookMaintenanceArtifactStore artifactStore;
  private final InterruptedPairCompanionBookVerifier companionBookVerifier;

  SqliteProtectedBookPairPublicationPreparation(
      SqliteProtectedBookMaintenanceArtifactStore artifactStore,
      InterruptedPairCompanionBookVerifier companionBookVerifier) {
    this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
    this.companionBookVerifier =
        Objects.requireNonNull(companionBookVerifier, "companionBookVerifier");
  }

  PreparedPairPublication prepare(
      Path normalizedSecretTargetPath,
      Path normalizedBookTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    Path secretTargetPath =
        Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath");
    Path bookTargetPath =
        Objects.requireNonNull(normalizedBookTargetPath, "normalizedBookTargetPath");
    RestoredBookTargetPolicy checkedBookTargetPolicy =
        Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy");
    ProtectedBookMaintenanceArtifactRole checkedBookArtifactRole =
        Objects.requireNonNull(bookArtifactRole, "bookArtifactRole");
    ProtectedBookMaintenanceArtifactRole checkedSecretArtifactRole =
        Objects.requireNonNull(secretArtifactRole, "secretArtifactRole");
    try (SqlitePairPublicationPreparationResources resources =
        new SqlitePairPublicationPreparationResources()) {
      resources.holdBookTargetLease(
          requireManagedTargetLease(bookTargetPath, checkedBookArtifactRole));
      resources.holdSecretTargetLease(
          requireManagedTargetLease(secretTargetPath, checkedSecretArtifactRole));
      recoverInterruptedPublication(secretTargetPath, bookTargetPath);
      if (checkedBookTargetPolicy == RestoredBookTargetPolicy.REQUIRE_ABSENT) {
        resources.holdBookReservation(
            reserveAbsentBookTarget(bookTargetPath, checkedBookArtifactRole));
      }
      SqliteGeneratedSecretTarget.requireAbsent(secretTargetPath);
      prepareGeneratedSecretTarget(
          secretTargetPath,
          checkedSecretArtifactRole,
          targetPath -> {
            SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(targetPath);
            SqliteBookKeyFileSecurity.ensureSecureParentDirectory(targetPath);
            SqliteGeneratedSecretTarget.requireAtomicNoReplacePublication(targetPath);
          });
      resources.holdSecretReservation(reserveAbsentSecretTarget(secretTargetPath));
      return resources.transferToPreparedPublication(
          bookTargetPath, secretTargetPath, checkedBookTargetPolicy);
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.SecretTargetOccupied(exception.targetPath()),
          exception);
    }
  }

  void recoverInterruptedPublication(
      Path normalizedSecretTargetPath, Path normalizedBookTargetPath) {
    Path secretTargetPath =
        Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath");
    Path bookTargetPath =
        Objects.requireNonNull(normalizedBookTargetPath, "normalizedBookTargetPath");
    Path secretTargetParent = secretTargetPath.getParent();
    if (secretTargetParent == null
        || !Files.isDirectory(secretTargetParent, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    List<SqliteOwnedStageRecord> secretStages = SqliteOwnedStageRecord.findFor(secretTargetPath);
    if (secretStages.isEmpty()) {
      return;
    }
    boolean publishedSecretIsOwned =
        secretStages.stream()
            .anyMatch(
                stage ->
                    SqliteProtectedBookPublicationRecovery.isSameOwnedStage(
                        secretTargetPath, stage.stagedPath()));
    if (!publishedSecretIsOwned) {
      discardRecoveredStages(secretStages);
      SqliteOwnedStagedArtifact.recoverFor(bookTargetPath);
      return;
    }
    if (companionBookVerifier.opens(bookTargetPath, secretTargetPath)) {
      discardRecoveredStages(secretStages);
      SqliteOwnedStagedArtifact.recoverFor(bookTargetPath);
      return;
    }
    SqliteProtectedBookPublicationRecovery.removeRecoveredSecret(secretTargetPath);
    removeOwnedCompanionBookIfPresent(bookTargetPath);
    discardRecoveredStages(secretStages);
    SqliteOwnedStagedArtifact.recoverFor(bookTargetPath);
  }

  static void prepareGeneratedSecretTarget(
      Path normalizedSecretTargetPath, GeneratedSecretTargetPreparation preparation) {
    Path checkedPath =
        Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath");
    Objects.requireNonNull(preparation, "preparation");
    try {
      preparation.prepare(checkedPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to prepare the generated FinGrind secret target " + checkedPath + ".", exception);
    }
  }

  static void prepareGeneratedSecretTarget(
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      GeneratedSecretTargetPreparation preparation) {
    try {
      prepareGeneratedSecretTarget(normalizedSecretTargetPath, preparation);
    } catch (SqliteCallerPathContractException exception) {
      throw secretTargetPathRejection(secretArtifactRole, exception);
    }
  }

  static SqliteOwnedDestinationReservation reserveAbsentBookTarget(
      Path bookTargetPath, ProtectedBookMaintenanceArtifactRole bookArtifactRole) {
    return reserveAbsentBookTarget(
        bookTargetPath, bookArtifactRole, SqliteOwnedDestinationReservation::reserve);
  }

  static SqliteOwnedDestinationReservation reserveAbsentBookTarget(
      Path bookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      DestinationReservationCreator reservationCreator) {
    try {
      SqliteProtectedBookStagingFiles.ensureSecureBackupFileParentDirectory(bookTargetPath);
      return Objects.requireNonNull(reservationCreator, "reservationCreator")
          .reserve(bookTargetPath);
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          bookArtifactRole, exception);
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      throw new ProtectedBookMaintenanceRejectionException(
          occupiedBookTargetRejection(bookArtifactRole, bookTargetPath), exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to reserve the FinGrind protected-book destination "
              + SqliteMachinePaths.absoluteValue(bookTargetPath)
              + ".",
          exception);
    }
  }

  static SqliteOwnedDestinationReservation reserveAbsentSecretTarget(Path secretTargetPath) {
    return reserveAbsentSecretTarget(secretTargetPath, SqliteOwnedDestinationReservation::reserve);
  }

  static SqliteOwnedDestinationReservation reserveAbsentSecretTarget(
      Path secretTargetPath, DestinationReservationCreator reservationCreator) {
    try {
      return Objects.requireNonNull(reservationCreator, "reservationCreator")
          .reserve(secretTargetPath);
    } catch (java.nio.file.FileAlreadyExistsException exception) {
      throw new SqliteGeneratedSecretTargetOccupiedException(secretTargetPath, exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to reserve the FinGrind generated-secret destination "
              + SqliteMachinePaths.absoluteValue(secretTargetPath)
              + ".",
          exception);
    }
  }

  private ProtectedBookMaintenanceStore.HeldLease requireManagedTargetLease(
      Path targetPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    return switch (artifactStore.acquireManagedArtifactLease(targetPath, artifactRole)) {
      case ProtectedBookMaintenanceStore.HeldLease heldLease -> heldLease;
      case ProtectedBookMaintenanceStore.LeaseBusy leaseBusy ->
          throw new ProtectedBookMaintenanceRejectionException(
              new ProtectedBookMaintenanceRejection.ArtifactBusy(
                  artifactRole, leaseBusy.artifactPath()));
    };
  }

  static ProtectedBookMaintenanceRejectionException secretTargetPathRejection(
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      SqliteCallerPathContractException exception) {
    return SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
        secretArtifactRole, exception);
  }

  static ProtectedBookMaintenanceRejection occupiedBookTargetRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole, Path bookTargetPath) {
    return switch (artifactRole) {
      case BACKUP_TARGET ->
          new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(bookTargetPath);
      case LIVE_BOOK, RESTORED_TARGET ->
          new ProtectedBookMaintenanceRejection.BookDestinationOccupied(bookTargetPath);
      case BACKUP_SOURCE, BACKUP_KEY_TARGET ->
          throw new IllegalArgumentException(
              "An absent protected-book target cannot use artifact role " + artifactRole + ".");
    };
  }

  private static void removeOwnedCompanionBookIfPresent(Path bookTargetPath) {
    boolean publishedBookIsOwned =
        SqliteOwnedStageRecord.findFor(bookTargetPath).stream()
            .anyMatch(
                stage ->
                    SqliteProtectedBookPublicationRecovery.isSameOwnedStage(
                        bookTargetPath, stage.stagedPath()));
    if (publishedBookIsOwned) {
      SqliteProtectedBookPublicationRecovery.removeRecoveredArtifact(bookTargetPath);
    }
  }

  private static void discardRecoveredStages(List<SqliteOwnedStageRecord> stages) {
    for (SqliteOwnedStageRecord stage : stages) {
      stage.discard();
    }
  }
}
