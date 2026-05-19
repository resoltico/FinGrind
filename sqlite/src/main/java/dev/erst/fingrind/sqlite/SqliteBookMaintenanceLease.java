package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Exclusive same-path maintenance lease used to coordinate destructive protected-book workflows.
 */
final class SqliteBookMaintenanceLease {
  private static final String LEASE_SUFFIX = ".fingrind-maintenance.lock";
  private static final Duration INCOMPLETE_LEASE_GRACE_PERIOD = Duration.ofSeconds(5);
  private static final ThreadLocal<Set<Path>> OWNED_ARTIFACT_PATHS =
      ThreadLocal.withInitial(HashSet::new);

  private SqliteBookMaintenanceLease() {}

  static ProtectedBookLeaseAcquisition acquire(Path normalizedArtifactPath) {
    Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    if (currentThreadOwns(normalizedArtifactPath)) {
      throw new IllegalStateException(
          "The current thread already owns the FinGrind maintenance lease for "
              + normalizedArtifactPath
              + ".");
    }
    Path leasePath = leasePath(normalizedArtifactPath);
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedArtifactPath);
      if (!acquireLeaseFile(leasePath)) {
        return new LeaseBusy(normalizedArtifactPath);
      }
      if (SqliteNativeBootstrap.activeConnectionCount(normalizedArtifactPath) > 0
          || SqliteNativeBootstrap.hasExternalActiveConnections(normalizedArtifactPath)) {
        releaseLeaseFile(leasePath);
        return new LeaseBusy(normalizedArtifactPath);
      }
      OWNED_ARTIFACT_PATHS.get().add(normalizedArtifactPath);
      return new HeldLease(normalizedArtifactPath, leasePath);
    } catch (IOException exception) {
      releaseLeaseFileQuietly(leasePath);
      throw new IllegalStateException(
          "Failed to acquire one FinGrind maintenance lease.", exception);
    } catch (RuntimeException | Error exception) {
      releaseLeaseFileQuietly(leasePath);
      throw exception;
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
      if (existingLeaseIsBusy(leasePath)) {
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

  private static boolean acquireLeaseFile(Path leasePath) throws IOException {
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        Files.writeString(
            leasePath,
            SqliteProcessIdentity.current().leaseMetadataText(),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE);
        SqliteBookFileSecurity.hardenOwnerOnlyFile(leasePath);
        return true;
      } catch (FileAlreadyExistsException ignored) {
        if (existingLeaseIsBusy(leasePath)) {
          return false;
        }
        releaseLeaseFile(leasePath);
      } catch (IOException exception) {
        releaseLeaseFileQuietly(leasePath);
        throw exception;
      }
    }
    return false;
  }

  private static boolean existingLeaseIsBusy(Path leasePath) throws IOException {
    if (!Files.exists(leasePath, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    String leaseContents = Files.readString(leasePath);
    SqliteProcessIdentity leaseOwner = SqliteProcessIdentity.fromLeaseMetadata(leaseContents);
    if (leaseOwner != null) {
      return leaseOwner.isLive();
    }
    Instant lastModified =
        Files.getLastModifiedTime(leasePath, LinkOption.NOFOLLOW_LINKS).toInstant();
    return lastModified.plus(INCOMPLETE_LEASE_GRACE_PERIOD).isAfter(Instant.now());
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
    private final Path leasePath;
    private boolean closed;

    private HeldLease(Path artifactPath, Path leasePath) {
      this.artifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
      this.leasePath = Objects.requireNonNull(leasePath, "leasePath");
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
      releaseLeaseFileQuietly(leasePath);
    }
  }
}
