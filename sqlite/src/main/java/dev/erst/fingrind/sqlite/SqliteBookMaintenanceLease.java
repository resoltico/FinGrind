package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
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

  /** Intent for acquiring one maintenance lease around an existing artifact or a managed target. */
  enum LeaseIntent {
    /** Lease one artifact that must already exist as a regular file. */
    EXISTING_ARTIFACT,
    /** Lease one target path that FinGrind may prepare and publish into. */
    MANAGED_TARGET
  }

  static ProtectedBookLeaseAcquisition acquire(
      Path normalizedArtifactPath, LeaseIntent leaseIntent) {
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
      if (leaseIntent == LeaseIntent.MANAGED_TARGET) {
        SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedArtifactPath);
      } else {
        requireExistingArtifact(normalizedArtifactPath);
      }
      LeaseFileHandle leaseFileHandle = acquireLeaseFile(leasePath);
      if (leaseFileHandle == null) {
        return new LeaseBusy(normalizedArtifactPath);
      }
      if (SqliteNativeBootstrap.activeConnectionCount(normalizedArtifactPath) > 0
          || SqliteNativeBootstrap.hasExternalActiveConnections(normalizedArtifactPath)) {
        leaseFileHandle.closeAndDelete();
        return new LeaseBusy(normalizedArtifactPath);
      }
      OWNED_ARTIFACT_PATHS.get().add(normalizedArtifactPath);
      return new HeldLease(normalizedArtifactPath, leaseFileHandle);
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

  private static @Nullable LeaseFileHandle acquireLeaseFile(Path leasePath) throws IOException {
    for (int attempt = 0; attempt < MAX_STALE_RECLAIM_ATTEMPTS; attempt++) {
      switch (tryCreateLeaseFile(leasePath)) {
        case CreatedLease(LeaseFileHandle leaseFileHandle) -> {
          return leaseFileHandle;
        }
        case ExistingLease ignored -> {
          if (leaseLooksLive(leasePath)) {
            return null;
          }
          releaseLeaseFileQuietly(leasePath);
        }
      }
    }
    return null;
  }

  private static LeaseCreation tryCreateLeaseFile(Path leasePath) throws IOException {
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
      return new CreatedLease(new LeaseFileHandle(leasePath));
    } catch (FileAlreadyExistsException ignored) {
      return new ExistingLease(leasePath);
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

  private static void releaseLeaseFileQuietly(Path leasePath) {
    try {
      releaseLeaseFile(leasePath);
    } catch (IOException exception) {
      SqliteBestEffort.reportCleanupFailure(
          "deleting one SQLite maintenance lease file", exception);
    }
  }

  /** Lease-acquisition result for one protected-book artifact path. */
  sealed interface ProtectedBookLeaseAcquisition permits HeldLease, LeaseBusy {
    /** Returns the normalized artifact path that this acquisition outcome belongs to. */
    Path artifactPath();
  }

  /** Busy outcome when one exclusive maintenance lease could not be acquired. */
  record LeaseBusy(Path artifactPath) implements ProtectedBookLeaseAcquisition {
    LeaseBusy {
      Objects.requireNonNull(artifactPath, "artifactPath");
    }
  }

  /** Held maintenance lease that blocks concurrent destructive workflows on one artifact path. */
  static final class HeldLease
      implements ProtectedBookLeaseAcquisition, ProtectedBookMaintenanceStore.HeldLease {
    private final Path artifactPath;
    private final LeaseFileHandle leaseFileHandle;
    private boolean closed;

    private HeldLease(Path artifactPath, LeaseFileHandle leaseFileHandle) {
      this.artifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
      this.leaseFileHandle = Objects.requireNonNull(leaseFileHandle, "leaseFileHandle");
    }

    @Override
    public Path artifactPath() {
      return artifactPath;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      OWNED_ARTIFACT_PATHS.get().remove(artifactPath);
      leaseFileHandle.closeAndDelete();
    }
  }

  /** Result of attempting to claim a cooperative maintenance lease file for one artifact path. */
  private sealed interface LeaseCreation permits CreatedLease, ExistingLease {}

  /** Freshly created lease file owned by the current workflow. */
  private record CreatedLease(LeaseFileHandle leaseFileHandle) implements LeaseCreation {
    private CreatedLease {
      Objects.requireNonNull(leaseFileHandle, "leaseFileHandle");
    }
  }

  /** Pre-existing lease file discovered while another workflow owns the artifact path. */
  private record ExistingLease(Path leasePath) implements LeaseCreation {
    private ExistingLease {
      Objects.requireNonNull(leasePath, "leasePath");
    }
  }

  /** Detached lease-file owner that survives until the workflow closes it. */
  private static final class LeaseFileHandle {
    private final Path leasePath;

    private LeaseFileHandle(Path leasePath) {
      this.leasePath = Objects.requireNonNull(leasePath, "leasePath");
    }

    private void closeAndDelete() {
      releaseLeaseFileQuietly(leasePath);
    }
  }
}
