package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.SystemUtcClock;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Naming, acquisition, and stale-artifact scanning for same-directory maintenance leases. */
final class SqliteMaintenanceLeaseArtifacts {
  private static final String LEASE_PREFIX_SEGMENT = ".fingrind-maintenance-";
  private static final String LEASE_SUFFIX = ".lock";
  private static final String LEGACY_LEASE_SUFFIX = ".fingrind-maintenance.lock";
  private static final Duration INCOMPLETE_LEASE_GRACE_PERIOD = Duration.ofSeconds(5);
  private static final Duration UNKNOWN_START_EXTERNAL_LEASE_GRACE_PERIOD = Duration.ofMinutes(15);
  private static final int MAX_STALE_RECLAIM_ATTEMPTS = 8;

  private SqliteMaintenanceLeaseArtifacts() {}

  static @Nullable SqliteLeaseHandle acquire(Path normalizedArtifactPath) throws IOException {
    LeasePaths leasePaths = leasePaths(normalizedArtifactPath);
    for (int attempt = 0; attempt < MAX_STALE_RECLAIM_ATTEMPTS; attempt++) {
      if (hasLiveArtifact(normalizedArtifactPath)) {
        return null;
      }
      try {
        Files.createDirectory(leasePaths.currentProcessLeasePath());
        try {
          SqliteBookFileSecurity.hardenDirectory(leasePaths.currentProcessLeasePath());
        } catch (IOException | RuntimeException | Error exception) {
          SqliteBookMaintenanceLease.releaseLeaseArtifactQuietly(
              leasePaths.currentProcessLeasePath());
          throw exception;
        }
        return new SqliteLeaseHandle(leasePaths.currentProcessLeasePath());
      } catch (FileAlreadyExistsException ignored) {
        // Another thread or process won the race; rescan and retry.
      }
    }
    return null;
  }

  static boolean hasLiveArtifact(Path normalizedArtifactPath) throws IOException {
    LeasePaths leasePaths = leasePaths(normalizedArtifactPath);
    return hasLiveLegacyArtifact(leasePaths) || hasLiveSiblingArtifact(leasePaths);
  }

  private static boolean hasLiveLegacyArtifact(LeasePaths leasePaths) throws IOException {
    if (!Files.exists(leasePaths.legacyLeasePath(), LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    return legacyLeaseLooksLive(leasePaths.legacyLeasePath())
        || !deleteArtifactAndVerifyMissing(leasePaths.legacyLeasePath());
  }

  private static boolean hasLiveSiblingArtifact(LeasePaths leasePaths) throws IOException {
    Path parentDirectory = leasePaths.normalizedArtifactPath().getParent();
    if (parentDirectory == null || !Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    SiblingArtifactScanner scanner = new SiblingArtifactScanner(parentDirectory, leasePaths);
    Files.walkFileTree(parentDirectory, java.util.Set.of(), 2, scanner);
    return scanner.liveArtifactFound();
  }

  private static boolean isLiveSiblingArtifact(Path sibling, LeasePaths leasePaths)
      throws IOException {
    String siblingFileName =
        Objects.requireNonNull(sibling.getFileName(), "sibling fileName").toString();
    if ((!siblingFileName.startsWith(leasePaths.expectedPrefix())
            || !siblingFileName.endsWith(LEASE_SUFFIX))
        || !Files.isDirectory(sibling, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    SqliteProcessIdentity leaseOwner =
        SqliteProcessIdentity.fromCoordinationToken(
            siblingFileName.substring(
                leasePaths.expectedPrefix().length(),
                siblingFileName.length() - LEASE_SUFFIX.length()));
    if (leaseOwner == null) {
      return !deleteArtifactAndVerifyMissing(sibling);
    }
    return leaseOwnerOrUndeletableArtifact(sibling, leaseOwner);
  }

  private static boolean leaseOwnerOrUndeletableArtifact(
      Path sibling, SqliteProcessIdentity leaseOwner) throws IOException {
    Instant lastModified =
        Files.getLastModifiedTime(sibling, LinkOption.NOFOLLOW_LINKS).toInstant();
    return leaseOwner.isLiveWhenUnlocked(lastModified, UNKNOWN_START_EXTERNAL_LEASE_GRACE_PERIOD)
        || !deleteArtifactAndVerifyMissing(sibling);
  }

  private static boolean legacyLeaseLooksLive(Path leasePath) throws IOException {
    try {
      String leaseContents = Files.readString(leasePath);
      SqliteProcessIdentity leaseOwner = SqliteProcessIdentity.fromLeaseMetadata(leaseContents);
      Instant lastModified =
          Files.getLastModifiedTime(leasePath, LinkOption.NOFOLLOW_LINKS).toInstant();
      return leaseOwner != null
          ? leaseOwner.isLiveWhenUnlocked(lastModified, UNKNOWN_START_EXTERNAL_LEASE_GRACE_PERIOD)
          : lastModified
              .plus(INCOMPLETE_LEASE_GRACE_PERIOD)
              .isAfter(Instant.now(SystemUtcClock.instance()));
    } catch (NoSuchFileException ignored) {
      return false;
    }
  }

  private static boolean deleteArtifactAndVerifyMissing(Path leasePath) throws IOException {
    Files.deleteIfExists(leasePath);
    return !Files.exists(leasePath, LinkOption.NOFOLLOW_LINKS);
  }

  private static LeasePaths leasePaths(Path normalizedArtifactPath) {
    String fileName =
        Objects.requireNonNull(
                normalizedArtifactPath.getFileName(), "normalizedArtifactPath fileName")
            .toString();
    String expectedPrefix = fileName + LEASE_PREFIX_SEGMENT;
    return new LeasePaths(
        normalizedArtifactPath,
        expectedPrefix,
        normalizedArtifactPath.resolveSibling(
            expectedPrefix + SqliteProcessIdentity.current().coordinationToken() + LEASE_SUFFIX),
        normalizedArtifactPath.resolveSibling(fileName + LEGACY_LEASE_SUFFIX));
  }

  private record LeasePaths(
      Path normalizedArtifactPath,
      String expectedPrefix,
      Path currentProcessLeasePath,
      Path legacyLeasePath) {}

  /** Walks same-directory entries without introducing try-with-resources bytecode branches here. */
  private static final class SiblingArtifactScanner extends SimpleFileVisitor<Path> {
    private final Path parentDirectory;
    private final LeasePaths leasePaths;
    private boolean liveArtifactFound;

    private SiblingArtifactScanner(Path parentDirectory, LeasePaths leasePaths) {
      this.parentDirectory = parentDirectory;
      this.leasePaths = leasePaths;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
        throws IOException {
      if (directory.equals(parentDirectory)) {
        return FileVisitResult.CONTINUE;
      }
      return inspect(directory) == FileVisitResult.CONTINUE
          ? FileVisitResult.SKIP_SUBTREE
          : FileVisitResult.TERMINATE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
      return inspect(file);
    }

    private FileVisitResult inspect(Path sibling) throws IOException {
      if (!isLiveSiblingArtifact(sibling, leasePaths)) {
        return FileVisitResult.CONTINUE;
      }
      liveArtifactFound = true;
      return FileVisitResult.TERMINATE;
    }

    private boolean liveArtifactFound() {
      return liveArtifactFound;
    }
  }
}
