package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.PreparedPairPublication;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.RestoredBookTargetPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Capability, reservation, and filesystem admission for pair-publication target names. */
final class SqliteProtectedBookPairPublicationTargets {
  private SqliteProtectedBookPairPublicationTargets() {}

  /** Acquires all retained final-name capability witnesses for one prepared pair. */
  @FunctionalInterface
  interface PairPublicationWitnessAcquirer {
    /** Acquires the complete immutable requirement set for one pair publication. */
    SqlitePublicationCapabilityWitness.Set acquire(
        List<SqlitePublicationCapabilityWitness.Requirement> requirements) throws IOException;
  }

  static PreparedPairPublication prepareWithHeldLeases(
      SqlitePairPublicationPreparationResources resources,
      Path secretTargetPath,
      Path bookTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    SqlitePairPublicationPreparationResources checkedResources =
        Objects.requireNonNull(resources, "resources");
    try {
      SqliteGeneratedSecretTarget.requireAbsent(secretTargetPath);
      prepareGeneratedSecretTarget(
          secretTargetPath,
          secretArtifactRole,
          targetPath -> {
            SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(targetPath);
            SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(targetPath);
          });
      checkedResources.holdCapabilityWitnesses(
          acquirePairPublicationWitnesses(
              bookTargetPath,
              secretTargetPath,
              bookTargetPolicy,
              bookArtifactRole,
              secretArtifactRole));
      if (bookTargetPolicy == RestoredBookTargetPolicy.REQUIRE_ABSENT) {
        checkedResources.holdBookReservation(
            reserveAbsentBookTarget(bookTargetPath, bookArtifactRole));
      }
      checkedResources.holdSecretReservation(reserveAbsentSecretTarget(secretTargetPath));
      return checkedResources.transferToPreparedPublication(
          bookTargetPath, secretTargetPath, bookTargetPolicy);
    } catch (SqliteGeneratedSecretTargetOccupiedException exception) {
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.SecretTargetOccupied(exception.targetPath()),
          exception);
    }
  }

  private static SqlitePublicationCapabilityWitness.Set acquirePairPublicationWitnesses(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    return acquirePairPublicationWitnesses(
        bookTargetPath,
        secretTargetPath,
        bookTargetPolicy,
        bookArtifactRole,
        secretArtifactRole,
        requirements ->
            SqlitePublicationCapabilityWitness.acquire(
                requirements,
                java.nio.file.Files::createLink,
                SqliteProtectedBookPublicationSupport::moveReplacing));
  }

  /**
   * Acquires retained pair-publication witnesses through an explicit failure boundary.
   *
   * <p>The package-visible acquirer keeps role-specific capability failures directly executable:
   * the selected final target, not an implementation detail, owns every reported rejection.
   */
  static SqlitePublicationCapabilityWitness.Set acquirePairPublicationWitnesses(
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      PairPublicationWitnessAcquirer witnessAcquirer) {
    try {
      List<SqlitePublicationCapabilityWitness.Requirement> requirements = new ArrayList<>();
      switch (Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy")) {
        case REQUIRE_ABSENT ->
            requirements.add(
                SqlitePublicationCapabilityWitness.Requirement.noReplace(bookTargetPath));
        case REPLACE_SELECTED -> {
          requirements.add(
              SqlitePublicationCapabilityWitness.Requirement.atomicReplace(bookTargetPath));
          requirements.add(
              SqlitePublicationCapabilityWitness.Requirement.noReplace(bookTargetPath));
        }
      }
      requirements.add(SqlitePublicationCapabilityWitness.Requirement.noReplace(secretTargetPath));
      return Objects.requireNonNull(witnessAcquirer, "witnessAcquirer").acquire(requirements);
    } catch (SqlitePublicationCapabilityWitness.AcquisitionFailure failure) {
      throw capabilityAcquisitionFailure(
          failure,
          bookTargetPath,
          secretTargetPath,
          bookTargetPolicy,
          bookArtifactRole,
          secretArtifactRole);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire retained FinGrind publication capability witnesses.", exception);
    }
  }

  static void requireRecoveryTargetSecurity(
      Path bookTargetPath,
      Path secretTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    requireBookTargetSecurity(bookTargetPath, bookArtifactRole);
    try {
      SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(secretTargetPath);
      SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(secretTargetPath);
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          Objects.requireNonNull(secretArtifactRole, "secretArtifactRole"), exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to revalidate protected-book pair recovery target directories.", exception);
    }
  }

  /**
   * Admits the two already-normalized final targets before any lease-control, reservation, stage,
   * capability-witness, claim, or recovery-evidence mutation.
   *
   * <p>Caller-selected missing parents are deliberately created and admitted earlier by the
   * maintenance path boundary. Identity cannot be established for an absent parent; this method
   * therefore protects every later workflow artifact while preserving that explicit parent policy.
   */
  static void requirePrepublicationPairTargetAdmission(
      Path bookTargetPath,
      Path secretTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    Path checkedBookTarget = Objects.requireNonNull(bookTargetPath, "bookTargetPath");
    Path checkedSecretTarget = Objects.requireNonNull(secretTargetPath, "secretTargetPath");
    ProtectedBookMaintenanceArtifactRole checkedBookRole =
        Objects.requireNonNull(bookArtifactRole, "bookArtifactRole");
    ProtectedBookMaintenanceArtifactRole checkedSecretRole =
        Objects.requireNonNull(secretArtifactRole, "secretArtifactRole");
    requireRecoveryTargetSecurity(
        checkedBookTarget, checkedSecretTarget, checkedBookRole, checkedSecretRole);
    final boolean sameFinalIdentity;
    try {
      sameFinalIdentity =
          SqlitePairTargetIdentity.sameFinalTargetIdentity(checkedBookTarget, checkedSecretTarget);
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
                  exception.requestedPath(), checkedSecretTarget)
              ? checkedSecretRole
              : checkedBookRole,
          exception);
    }
    if (sameFinalIdentity) {
      throw new ProtectedBookMaintenanceRejectionException(
          new ProtectedBookMaintenanceRejection.PairTargetsConflict(
              checkedBookTarget, checkedSecretTarget));
    }
  }

  static void prepareGeneratedSecretTarget(
      Path normalizedSecretTargetPath,
      SqliteProtectedBookPairPublicationPreparation.GeneratedSecretTargetPreparation preparation) {
    Path checkedPath =
        Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath");
    try {
      Objects.requireNonNull(preparation, "preparation").prepare(checkedPath);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to prepare the generated FinGrind secret target " + checkedPath + ".", exception);
    }
  }

  static void prepareGeneratedSecretTarget(
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      SqliteProtectedBookPairPublicationPreparation.GeneratedSecretTargetPreparation preparation) {
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
      SqliteProtectedBookPairPublicationPreparation.DestinationReservationCreator
          reservationCreator) {
    try {
      SqliteProtectedBookStagingFiles.requireExistingSecureBackupFileParentDirectory(
          bookTargetPath);
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
      Path secretTargetPath,
      SqliteProtectedBookPairPublicationPreparation.DestinationReservationCreator
          reservationCreator) {
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

  static ProtectedBookMaintenanceRejectionException secretTargetPathRejection(
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      SqliteCallerPathContractException exception) {
    return SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
        secretArtifactRole, exception);
  }

  /** Translates one retained-witness admission failure to the exact admitted artifact role. */
  static RuntimeException capabilityAcquisitionFailure(
      SqlitePublicationCapabilityWitness.AcquisitionFailure failure,
      Path bookTargetPath,
      Path secretTargetPath,
      RestoredBookTargetPolicy bookTargetPolicy,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    SqlitePublicationCapabilityWitness.AcquisitionFailure checkedFailure =
        Objects.requireNonNull(failure, "failure");
    Path failedTarget = checkedFailure.requirement().targetPath();
    boolean bookTarget =
        SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            failedTarget, Objects.requireNonNull(bookTargetPath, "bookTargetPath"));
    boolean secretTarget =
        SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            failedTarget, Objects.requireNonNull(secretTargetPath, "secretTargetPath"));
    if (!bookTarget && !secretTarget) {
      return new IllegalStateException(
          "A retained FinGrind publication capability witness failed for an unadmitted target "
              + failedTarget
              + ".",
          checkedFailure);
    }
    SqliteCallerPathFailure noReplaceFailure =
        bookTarget
            ? Objects.requireNonNull(bookTargetPolicy, "bookTargetPolicy")
                    == RestoredBookTargetPolicy.REPLACE_SELECTED
                ? SqliteCallerPathFailure.ATOMIC_BOOK_REPLACEMENT_UNSUPPORTED
                : SqliteCallerPathFailure.ATOMIC_BOOK_PUBLICATION_UNSUPPORTED
            : SqliteCallerPathFailure.ATOMIC_SECRET_PUBLICATION_UNSUPPORTED;
    @org.jspecify.annotations.Nullable SqliteCallerPathContractException pathFailure =
        SqlitePublicationCapabilityWitness.callerPathFailure(checkedFailure, noReplaceFailure);
    if (pathFailure != null) {
      ProtectedBookMaintenanceArtifactRole artifactRole =
          bookTarget
              ? Objects.requireNonNull(bookArtifactRole, "bookArtifactRole")
              : Objects.requireNonNull(secretArtifactRole, "secretArtifactRole");
      return SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          artifactRole, pathFailure);
    }
    return new IllegalStateException(
        "Failed to acquire the retained FinGrind publication capability witness for "
            + failedTarget
            + ".",
        checkedFailure);
  }

  static ProtectedBookMaintenanceRejection occupiedBookTargetRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole, Path bookTargetPath) {
    return switch (artifactRole) {
      case BACKUP_TARGET ->
          new ProtectedBookMaintenanceRejection.BackupDestinationAlreadyExists(bookTargetPath);
      case LIVE_BOOK, RESTORED_TARGET ->
          new ProtectedBookMaintenanceRejection.BookDestinationOccupied(bookTargetPath);
      case LIVE_BOOK_KEY_SOURCE,
          BACKUP_SOURCE,
          BACKUP_KEY_SOURCE,
          BACKUP_KEY_TARGET,
          NEW_BOOK_KEY_TARGET ->
          throw new IllegalArgumentException(
              "An absent protected-book target cannot use artifact role " + artifactRole + ".");
    };
  }

  private static void requireBookTargetSecurity(
      Path bookTargetPath, ProtectedBookMaintenanceArtifactRole bookArtifactRole) {
    try {
      SqliteBookFileSecurity.requireSupportedSecureFilesystem(bookTargetPath);
      SqliteBookFileSecurity.requireExistingSecureParentDirectory(bookTargetPath);
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          Objects.requireNonNull(bookArtifactRole, "bookArtifactRole"), exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to revalidate protected-book pair recovery target directories.", exception);
    }
  }
}
