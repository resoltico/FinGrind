package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Validates the filesystem identity and private-parent policy of pair-publication target names. */
final class SqliteProtectedBookPairTargetSecurity {
  private SqliteProtectedBookPairTargetSecurity() {}

  /** Validates one already-normalized final target before it enters pair-publication admission. */
  @FunctionalInterface
  interface TargetSecurityValidator {
    /** Validates the selected final target's filesystem and owner-only parent contract. */
    void validate(Path normalizedTargetPath) throws IOException;
  }

  static void requireRecoveryTargetSecurity(
      Path bookTargetPath,
      Path secretTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    requireRecoveryTargetSecurity(
        bookTargetPath,
        secretTargetPath,
        bookArtifactRole,
        secretArtifactRole,
        targetPath -> {
          SqliteBookFileSecurity.requireSupportedSecureFilesystem(targetPath);
          SqliteBookFileSecurity.requireExistingSecureParentDirectory(targetPath);
        },
        targetPath -> {
          SqliteBookKeyFileSecurity.requireSupportedSecureFilesystem(targetPath);
          SqliteBookKeyFileSecurity.requireExistingSecureParentDirectory(targetPath);
        });
  }

  /**
   * Validates pair targets through explicit filesystem boundaries before recovery mutates evidence.
   *
   * <p>The package-visible validators keep provider I/O failures accountable to the selected pair
   * admission rather than allowing an untested fallback to reclassify a caller's target.
   */
  static void requireRecoveryTargetSecurity(
      Path bookTargetPath,
      Path secretTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole,
      TargetSecurityValidator bookTargetSecurityValidator,
      TargetSecurityValidator secretTargetSecurityValidator) {
    try {
      Objects.requireNonNull(bookTargetSecurityValidator, "bookTargetSecurityValidator")
          .validate(bookTargetPath);
    } catch (SqliteCallerPathContractException exception) {
      throw SqliteProtectedBookMaintenanceArtifactStore.maintenanceRejection(
          Objects.requireNonNull(bookArtifactRole, "bookArtifactRole"), exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to revalidate protected-book pair recovery target directories.", exception);
    }
    try {
      Objects.requireNonNull(secretTargetSecurityValidator, "secretTargetSecurityValidator")
          .validate(secretTargetPath);
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
}
