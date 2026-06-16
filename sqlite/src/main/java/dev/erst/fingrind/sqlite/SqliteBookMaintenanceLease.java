package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Exclusive same-path maintenance lease used to coordinate destructive protected-book workflows.
 *
 * <p>The lease is owned by an atomically created sibling lock directory whose name embeds one
 * process-identity token. That protocol works across filesystems that do not support advisory
 * {@code FileLock} and avoids delete-tombstone churn on bind-mounted Docker volumes.
 */
final class SqliteBookMaintenanceLease {
  private static final ThreadLocal<Set<Path>> OWNED_ARTIFACT_PATHS =
      ThreadLocal.withInitial(HashSet::new);

  private SqliteBookMaintenanceLease() {}

  static SqliteProtectedBookLeaseAcquisition acquire(
      Path normalizedArtifactPath, SqliteMaintenanceLeaseIntent leaseIntent) {
    Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    Objects.requireNonNull(leaseIntent, "leaseIntent");
    if (currentThreadOwns(normalizedArtifactPath)) {
      throw new IllegalStateException(
          "The current thread already owns the FinGrind maintenance lease for "
              + normalizedArtifactPath
              + ".");
    }
    try {
      if (leaseIntent == SqliteMaintenanceLeaseIntent.MANAGED_TARGET) {
        SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedArtifactPath);
      } else {
        requireExistingArtifact(normalizedArtifactPath);
      }
      SqliteLeaseHandle leaseHandle =
          SqliteMaintenanceLeaseArtifacts.acquire(normalizedArtifactPath);
      if (leaseHandle == null) {
        return new SqliteLeaseBusy(normalizedArtifactPath);
      }
      if (SqliteNativeRuntimeActivity.activeConnectionCount(normalizedArtifactPath) > 0
          || SqliteNativeRuntimeActivity.hasExternalActiveConnections(normalizedArtifactPath)) {
        leaseHandle.closeAndDelete();
        return new SqliteLeaseBusy(normalizedArtifactPath);
      }
      OWNED_ARTIFACT_PATHS.get().add(normalizedArtifactPath);
      return new SqliteHeldLease(normalizedArtifactPath, leaseHandle, OWNED_ARTIFACT_PATHS);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire one FinGrind maintenance lease.", exception);
    }
  }

  private static void requireExistingArtifact(Path normalizedArtifactPath) {
    Path parent =
        Objects.requireNonNull(normalizedArtifactPath.getParent(), "normalizedArtifactPath parent");
    if (!Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The FinGrind maintenance lease requires one existing artifact parent directory: "
              + normalizedArtifactPath
              + ".");
    }
    if (!Files.isRegularFile(normalizedArtifactPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException(
          "The FinGrind maintenance lease requires one existing regular artifact file: "
              + normalizedArtifactPath
              + ".");
    }
  }

  static void requireNoActiveLease(Path normalizedArtifactPath) {
    Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    if (currentThreadOwns(normalizedArtifactPath)) {
      return;
    }
    try {
      if (SqliteMaintenanceLeaseArtifacts.hasLiveArtifact(normalizedArtifactPath)) {
        throw activeMaintenanceFailure(normalizedArtifactPath);
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect or clear one FinGrind maintenance lease artifact.", exception);
    }
  }

  private static ContractFailureException activeMaintenanceFailure(Path normalizedArtifactPath) {
    PublicPathHint artifactHint = PublicPathHint.fromPath(normalizedArtifactPath);
    return new ContractFailureException(
        ContractErrors.Descriptor.BOOK_MAINTENANCE_IN_PROGRESS.failure(
            "Book access refused because one active FinGrind maintenance workflow holds "
                + artifactHint.value()
                + ".",
            "Wait for the active maintenance workflow to finish, or clear the abandoned maintenance state through the dedicated maintenance and recovery commands before rerunning this command.",
            null));
  }

  private static boolean currentThreadOwns(Path normalizedArtifactPath) {
    return OWNED_ARTIFACT_PATHS.get().contains(normalizedArtifactPath);
  }

  static void releaseLeaseArtifactQuietly(Path leasePath) {
    SqliteFileCleanup.deleteQuietly(
        leasePath,
        (ignoredAction, exception) ->
            SqliteBestEffort.reportCleanupFailure(
                "deleting one SQLite maintenance lease artifact", exception));
  }
}
