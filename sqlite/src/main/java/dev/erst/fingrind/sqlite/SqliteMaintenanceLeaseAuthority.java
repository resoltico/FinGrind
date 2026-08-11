package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Resolves exact artifact authority and activity facts for maintenance-lease operations. */
final class SqliteMaintenanceLeaseAuthority {
  private SqliteMaintenanceLeaseAuthority() {}

  static void validateArtifactForLeaseIntent(
      Path normalizedArtifactPath, SqliteMaintenanceLeaseIntent leaseIntent) throws IOException {
    if (Objects.requireNonNull(leaseIntent, "leaseIntent")
        == SqliteMaintenanceLeaseIntent.MANAGED_TARGET) {
      SqliteBookFileSecurity.requireExistingSecureParentDirectory(normalizedArtifactPath);
      return;
    }
    requireExistingArtifact(normalizedArtifactPath);
  }

  static void requireNoActiveLease(Path normalizedArtifactPath) {
    Path checkedArtifactPath =
        Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    try {
      Path directoryDomain = canonicalDirectoryDomain(checkedArtifactPath);
      SqliteThreadMaintenanceLeases.DirectoryLease ownedLease =
          SqliteThreadMaintenanceLeases.directoryLease(directoryDomain);
      if (ownedLease != null
          && ownsExactArtifactOrPrivateDerivedStage(ownedLease, checkedArtifactPath)) {
        return;
      }
      if (SqliteMaintenanceLeaseArtifacts.hasBlockingArtifact(directoryDomain)) {
        throw activeMaintenanceFailure(checkedArtifactPath);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect one FinGrind maintenance lease artifact.", exception);
    }
  }

  static boolean currentThreadOwnsArtifactLease(Path normalizedArtifactPath) {
    Path checkedArtifactPath =
        Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    try {
      Path directoryDomain = canonicalDirectoryDomain(checkedArtifactPath);
      SqliteThreadMaintenanceLeases.DirectoryLease ownedLease =
          SqliteThreadMaintenanceLeases.directoryLease(directoryDomain);
      return ownedLease != null && ownedLease.owns(checkedArtifactPath);
    } catch (IOException | RuntimeException unavailableIdentity) {
      return false;
    }
  }

  static Path canonicalDirectoryDomain(Path normalizedArtifactPath) throws IOException {
    Path parent =
        Objects.requireNonNull(normalizedArtifactPath.getParent(), "normalizedArtifactPath parent");
    return parent.toRealPath();
  }

  static Path canonicalManagedTargetDirectory(Path normalizedArtifactPath) throws IOException {
    SqliteBookFileSecurity.requireExistingSecureParentDirectory(normalizedArtifactPath);
    return canonicalDirectoryDomain(normalizedArtifactPath);
  }

  static boolean hasBlockingActivity(Path artifactPath) {
    Path checkedArtifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
    return SqliteNativeRuntimeActivity.activeConnectionCount(checkedArtifactPath) > 0
        || SqliteNativeRuntimeActivity.hasExternalActiveConnections(checkedArtifactPath);
  }

  private static void requireExistingArtifact(Path normalizedArtifactPath) {
    Path parent =
        Objects.requireNonNull(normalizedArtifactPath.getParent(), "normalizedArtifactPath parent");
    if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          normalizedArtifactPath,
          SqliteCallerPathFailure.MISSING_PARENT_DIRECTORY,
          "The FinGrind maintenance lease requires one existing artifact parent directory: "
              + normalizedArtifactPath
              + ".");
    }
    if (!Files.isRegularFile(normalizedArtifactPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteCallerPathContractException(
          normalizedArtifactPath,
          SqliteCallerPathFailure.ARTIFACT_MUST_BE_REGULAR_NON_SYMLINK_FILE,
          "The FinGrind maintenance lease requires one existing regular artifact file: "
              + normalizedArtifactPath
              + ".");
    }
  }

  private static boolean ownsExactArtifactOrPrivateDerivedStage(
      SqliteThreadMaintenanceLeases.DirectoryLease ownedLease, Path normalizedArtifactPath) {
    SqliteThreadMaintenanceLeases.DirectoryLease checkedOwnedLease =
        Objects.requireNonNull(ownedLease, "ownedLease");
    Path checkedArtifactPath =
        Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    if (checkedOwnedLease.owns(checkedArtifactPath)) {
      return true;
    }
    @org.jspecify.annotations.Nullable Path journaledFinalTarget =
        SqliteJournaledStageAccess.finalTargetForCurrentThread(checkedArtifactPath);
    if (journaledFinalTarget != null && checkedOwnedLease.owns(journaledFinalTarget)) {
      return true;
    }
    @org.jspecify.annotations.Nullable Path finalTargetPath =
        SqliteOwnedStageRecord.soleCurrentFinalTargetForStage(checkedArtifactPath);
    return finalTargetPath != null && checkedOwnedLease.owns(finalTargetPath);
  }

  private static ContractFailureException activeMaintenanceFailure(Path normalizedArtifactPath) {
    return new ContractFailureException(
        ContractErrors.Descriptor.BOOK_MAINTENANCE_IN_PROGRESS.failureAt(
            normalizedArtifactPath,
            "Book access refused because one active or unresolved FinGrind maintenance workflow holds the selected protected-book directory.",
            "Wait for the active maintenance workflow to finish, or resolve the retained maintenance evidence through the dedicated maintenance and recovery commands before rerunning this command.",
            null));
  }
}
