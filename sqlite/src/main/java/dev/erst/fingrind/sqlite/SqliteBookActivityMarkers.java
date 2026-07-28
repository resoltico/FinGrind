package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Retained physical-object lock-slot coordination for active protected-book access across
 * processes.
 *
 * <p>Every identity is the mandatory stable filesystem object identity, not a caller path spelling.
 * Active connections hold one slot in the per-user object-control namespace; an unlocked valid
 * control file is inert after a process crash. Retired path-local control files are rejected rather
 * than interpreted by this protocol line.
 */
final class SqliteBookActivityMarkers {
  private static final String RETIRED_ACTIVITY_MARKER_SEGMENT = ".fingrind-activity-";
  private static final String RETIRED_ACTIVITY_MARKER_SUFFIX = ".marker";
  private static final String RETIRED_ACTIVITY_CONTROL_V2_PREFIX = ".fingrind-activity-v2-";
  private static final String RETIRED_ACTIVITY_CONTROL_SUFFIX = ".control";
  private static final Map<String, ActivityHandle> ACTIVE_BY_DOMAIN = new ConcurrentHashMap<>();
  private static final ReentrantLock ACTIVE_BY_DOMAIN_LOCK = new ReentrantLock();

  private SqliteBookActivityMarkers() {}

  /** Acquires one close-once activity registration bound to the current physical book object. */
  static ActivityRegistration acquireCurrentProcessActivity(Path normalizedBookPath) {
    Path checkedPath = normalized(Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"));
    try {
      BookDomain domain = domainFor(checkedPath);
      ACTIVE_BY_DOMAIN_LOCK.lock();
      try {
        ActivityHandle existing = ACTIVE_BY_DOMAIN.get(domain.identity());
        if (existing != null) {
          existing.retain();
          return new ActivityRegistration(domain.identity());
        }
        ActivityHandle acquired = acquire(domain);
        ACTIVE_BY_DOMAIN.put(domain.identity(), acquired);
        return new ActivityRegistration(domain.identity());
      } finally {
        ACTIVE_BY_DOMAIN_LOCK.unlock();
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire one FinGrind SQLite book activity control-file slot.", exception);
    }
  }

  private static void releaseCurrentProcessActivity(String objectIdentity) {
    ACTIVE_BY_DOMAIN_LOCK.lock();
    try {
      ActivityHandle handle =
          Objects.requireNonNull(
              ACTIVE_BY_DOMAIN.get(objectIdentity),
              "The FinGrind SQLite book activity registration was not owned by this process.");
      if (handle.release()) {
        ACTIVE_BY_DOMAIN.remove(objectIdentity, handle);
      }
    } finally {
      ACTIVE_BY_DOMAIN_LOCK.unlock();
    }
  }

  /** Returns whether a held activity slot or retired marker residue blocks this book identity. */
  static boolean hasExternalLiveMarker(Path normalizedBookPath) {
    Path checkedPath = normalized(Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"));
    if (!Files.isRegularFile(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    Path parentDirectory = requireParentDirectory(checkedPath);
    if (!Files.isDirectory(parentDirectory, LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    try {
      BookDomain domain = domainFor(checkedPath);
      return Files.exists(domain.objectDomain().controlPath(), LinkOption.NOFOLLOW_LINKS)
          && SqliteObjectCoordinationArtifacts.hasActiveSlot(checkedPath);
    } catch (IOException | RuntimeException unavailableOrMalformed) {
      // Coordination state that cannot be validated or probed is deliberately treated as live.
      return true;
    }
  }

  private static ActivityHandle acquire(BookDomain domain) throws IOException {
    SqliteThreadMaintenanceLeases.@Nullable ObjectLeaseReference maintenanceLease =
        SqliteThreadMaintenanceLeases.retainCurrentThreadObjectLease(domain.identity());
    if (maintenanceLease != null) {
      return ActivityHandle.forMaintenanceLease(maintenanceLease);
    }
    SqliteObjectCoordinationArtifacts.ActivitySlot slot =
        SqliteObjectCoordinationArtifacts.acquireActivitySlot(domain.objectDomain());
    return ActivityHandle.forActivitySlot(slot);
  }

  private static boolean hasRetiredMarkerResidue(Path parentDirectory, Path artifactPath)
      throws IOException {
    String retiredMarkerPrefix =
        Objects.requireNonNull(artifactPath.getFileName(), "artifactPath fileName").toString()
            + RETIRED_ACTIVITY_MARKER_SEGMENT;
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(parentDirectory)) {
      for (Path entry : entries) {
        String name = Objects.requireNonNull(entry.getFileName(), "entry fileName").toString();
        if ((name.startsWith(retiredMarkerPrefix) && name.endsWith(RETIRED_ACTIVITY_MARKER_SUFFIX))
            || (name.startsWith(RETIRED_ACTIVITY_CONTROL_V2_PREFIX)
                && name.endsWith(RETIRED_ACTIVITY_CONTROL_SUFFIX))) {
          return true;
        }
      }
    }
    return false;
  }

  private static BookDomain domainFor(Path normalizedBookPath) throws IOException {
    Path checkedPath = normalized(Objects.requireNonNull(normalizedBookPath, "normalizedBookPath"));
    SqliteBookFileSecurity.requireExistingSecureParentDirectory(checkedPath);
    Path parentDirectory =
        requireParentDirectory(checkedPath).toRealPath(LinkOption.NOFOLLOW_LINKS);
    if (hasRetiredMarkerResidue(parentDirectory, checkedPath)) {
      throw new IOException(
          "Retired FinGrind activity-control state blocks the current physical-object protocol.");
    }
    SqliteObjectCoordinationArtifacts.Domain objectDomain =
        SqliteObjectCoordinationArtifacts.domainForExistingArtifact(checkedPath);
    return new BookDomain(objectDomain.objectIdentity(), parentDirectory, objectDomain);
  }

  private static Path normalized(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
  }

  private static Path requireParentDirectory(Path normalizedBookPath) {
    return Objects.requireNonNull(
        normalizedBookPath.getParent(),
        "The FinGrind SQLite book path must resolve beneath a parent directory: "
            + normalizedBookPath);
  }

  private record BookDomain(
      String identity,
      Path parentDirectory,
      SqliteObjectCoordinationArtifacts.Domain objectDomain) {
    private BookDomain {
      Objects.requireNonNull(identity, "identity");
      Objects.requireNonNull(parentDirectory, "parentDirectory");
      Objects.requireNonNull(objectDomain, "objectDomain");
    }
  }

  /** Close-once current-process activity registration keyed without re-resolving the pathname. */
  static final class ActivityRegistration implements AutoCloseable {
    private final String objectIdentity;
    private boolean closed;

    private ActivityRegistration(String objectIdentity) {
      this.objectIdentity = Objects.requireNonNull(objectIdentity, "objectIdentity");
    }

    String objectIdentity() {
      return objectIdentity;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      closed = true;
      releaseCurrentProcessActivity(objectIdentity);
    }
  }

  /** Ref-counted local ownership of one retained activity or maintenance authority. */
  static final class ActivityHandle {
    private final ActivityAuthority authority;
    private int references = 1;

    ActivityHandle(ActivityAuthority authority) {
      this.authority = Objects.requireNonNull(authority, "authority");
    }

    static ActivityHandle forActivitySlot(
        SqliteObjectCoordinationArtifacts.ActivitySlot slot) {
      return new ActivityHandle(Objects.requireNonNull(slot, "slot")::close);
    }

    static ActivityHandle forMaintenanceLease(
        SqliteThreadMaintenanceLeases.ObjectLeaseReference maintenanceLease) {
      return new ActivityHandle(Objects.requireNonNull(maintenanceLease, "maintenanceLease")::release);
    }

    void retain() {
      references++;
    }

    /** Returns whether this handle was fully released. */
    boolean release() {
      if (references <= 0) {
        throw new IllegalStateException(
            "The FinGrind SQLite book activity handle was over-released.");
      }
      references--;
      if (references != 0) {
        return false;
      }
      try {
        authority.release();
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Failed to release one FinGrind SQLite book activity control-file slot.", exception);
      }
      return true;
    }
  }

  @FunctionalInterface
  interface ActivityAuthority {
    void release() throws IOException;
  }
}
