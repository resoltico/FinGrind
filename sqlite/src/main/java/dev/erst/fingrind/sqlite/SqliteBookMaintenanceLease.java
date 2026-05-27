package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Exclusive same-path maintenance lease used to coordinate destructive protected-book workflows.
 *
 * <p>The lease is owned by an atomically created sibling lock file plus process-liveness metadata.
 * That protocol works across filesystems that do not support advisory {@code FileLock}.
 */
final class SqliteBookMaintenanceLease {
  private static final String LEASE_SUFFIX = ".fingrind-maintenance.lock";
  private static final Duration INCOMPLETE_LEASE_GRACE_PERIOD = Duration.ofSeconds(5);
  private static final Duration UNKNOWN_START_EXTERNAL_LEASE_GRACE_PERIOD = Duration.ofMinutes(15);
  private static final int MAX_STALE_RECLAIM_ATTEMPTS = 8;
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
    Path leasePath = leasePath(normalizedArtifactPath);
    try {
      if (leaseIntent == SqliteMaintenanceLeaseIntent.MANAGED_TARGET) {
        SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedArtifactPath);
      } else {
        requireExistingArtifact(normalizedArtifactPath);
      }
      SqliteLeaseFileHandle leaseFileHandle = acquireLeaseFile(leasePath);
      if (leaseFileHandle == null) {
        return new SqliteLeaseBusy(normalizedArtifactPath);
      }
      if (SqliteNativeRuntimeActivity.activeConnectionCount(normalizedArtifactPath) > 0
          || SqliteNativeRuntimeActivity.hasExternalActiveConnections(normalizedArtifactPath)) {
        leaseFileHandle.closeAndDelete();
        return new SqliteLeaseBusy(normalizedArtifactPath);
      }
      OWNED_ARTIFACT_PATHS.get().add(normalizedArtifactPath);
      return new SqliteHeldLease(normalizedArtifactPath, leaseFileHandle, OWNED_ARTIFACT_PATHS);
    } catch (IOException exception) {
      releaseLeaseFileQuietly(leasePath);
      throw new IllegalStateException(
          "Failed to acquire one FinGrind maintenance lease.", exception);
    } catch (RuntimeException | Error exception) {
      releaseLeaseFileQuietly(leasePath);
      throw exception;
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
    Path leasePath = leasePath(normalizedArtifactPath);
    if (!Files.exists(leasePath, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try {
      if (leaseLooksLive(leasePath)) {
        throw activeMaintenanceFailure(normalizedArtifactPath);
      }
      releaseLeaseFile(leasePath);
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

  private static Path leasePath(Path normalizedArtifactPath) {
    String fileName =
        Objects.requireNonNull(
                normalizedArtifactPath.getFileName(), "normalizedArtifactPath fileName")
            .toString();
    return normalizedArtifactPath.resolveSibling(fileName + LEASE_SUFFIX);
  }

  private static @Nullable SqliteLeaseFileHandle acquireLeaseFile(Path leasePath)
      throws IOException {
    for (int attempt = 0; attempt < MAX_STALE_RECLAIM_ATTEMPTS; attempt++) {
      switch (tryCreateLeaseFile(leasePath)) {
        case SqliteCreatedLease(SqliteLeaseFileHandle leaseFileHandle) -> {
          return leaseFileHandle;
        }
        case SqliteExistingLease ignored -> {
          if (leaseLooksLive(leasePath)) {
            return null;
          }
          releaseLeaseFileQuietly(leasePath);
        }
      }
    }
    return null;
  }

  private static SqliteLeaseCreation tryCreateLeaseFile(Path leasePath) throws IOException {
    try {
      Files.writeString(
          leasePath,
          SqliteProcessIdentity.current().leaseMetadataText(),
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE);
      try {
        SqliteBookFileSecurity.hardenOwnerOnlyFile(leasePath);
      } catch (IOException | RuntimeException | Error exception) {
        releaseLeaseFileQuietly(leasePath);
        throw exception;
      }
      return new SqliteCreatedLease(new SqliteLeaseFileHandle(leasePath));
    } catch (FileAlreadyExistsException ignored) {
      return new SqliteExistingLease(leasePath);
    }
  }

  private static boolean leaseLooksLive(Path leasePath) throws IOException {
    try {
      String leaseContents = Files.readString(leasePath);
      SqliteProcessIdentity leaseOwner = SqliteProcessIdentity.fromLeaseMetadata(leaseContents);
      Instant lastModified =
          Files.getLastModifiedTime(leasePath, LinkOption.NOFOLLOW_LINKS).toInstant();
      if (leaseOwner != null) {
        return leaseOwner.isLiveWhenUnlocked(
            lastModified, UNKNOWN_START_EXTERNAL_LEASE_GRACE_PERIOD);
      }
      return lastModified.plus(INCOMPLETE_LEASE_GRACE_PERIOD).isAfter(Instant.now());
    } catch (NoSuchFileException ignored) {
      return false;
    }
  }

  private static void releaseLeaseFile(Path leasePath) throws IOException {
    Files.deleteIfExists(leasePath);
  }

  static void releaseLeaseFileQuietly(Path leasePath) {
    try {
      releaseLeaseFile(leasePath);
    } catch (IOException exception) {
      SqliteBestEffort.reportCleanupFailure(
          "deleting one SQLite maintenance lease file", exception);
    }
  }
}
