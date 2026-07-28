package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.PrivateOutputDirectory;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;

/**
 * Retained per-user O(1) coordination controls keyed by explicit physical-object identity.
 *
 * <p>A v4 object name is the SHA-256 of an explicit POSIX device/inode tuple or an explicit Windows
 * volume/file-id tuple. It is not a path spelling, a registry record, or an opaque provider {@code
 * fileKey} rendering. The immutable header binds the retained control to that same identity before
 * a lock is acquired.
 */
final class SqliteObjectCoordinationArtifacts {
  private static final String DEFAULT_ROOT_DIRECTORY = ".fingrind-coordination-v4";
  private static final String RETIRED_V3_ROOT_DIRECTORY = ".fingrind-coordination-v3";
  private static final String RETIRED_V2_ROOT_DIRECTORY = ".fingrind-coordination-v2";
  private static final String OBJECT_PREFIX = "object-v4-";
  private static final String CONTROL_SUFFIX = ".control";
  private static final String OBJECT_PROTOCOL = "FinGrind-object-coordination-v4";
  private static final String RETIRED_V3_OBJECT_PREFIX = "object-v3-";
  private static final String RETIRED_V2_OBJECT_PREFIX = "object-v2-";
  private static final String RETIRED_V4_REGISTRY_FILE = ".fingrind-object-registry-v4.control";
  static final int ACTIVITY_SLOT_COUNT = 1_024;
  private static final ReentrantLock TEST_ROOT_LOCK = new ReentrantLock();
  private static final Deque<Path> TEST_ROOT_STACK = new ArrayDeque<>();

  private SqliteObjectCoordinationArtifacts() {}

  /** Resolves one existing regular artifact into its immutable v4 O(1) control domain. */
  static Domain domainForExistingArtifact(Path existingArtifactPath) throws IOException {
    String objectIdentity = physicalIdentity(existingArtifactPath);
    Path root = requirePrivateRoot();
    Path controlPath =
        root.resolve(
            OBJECT_PREFIX
                + SqliteCoordinationControlFiles.sha256Hex(objectIdentity)
                + CONTROL_SUFFIX);
    return new Domain(
        objectIdentity,
        controlPath,
        SqliteCoordinationControlFiles.magic(OBJECT_PROTOCOL, objectIdentity));
  }

  /** Acquires the sole maintenance exclusion for one physical artifact, or reports it busy. */
  static @Nullable SqliteLeaseHandle tryAcquireMaintenanceExclusion(Path existingArtifactPath)
      throws IOException {
    return tryAcquireMaintenanceExclusion(domainForExistingArtifact(existingArtifactPath));
  }

  /** Acquires the sole maintenance exclusion for one already-resolved object-control domain. */
  static @Nullable SqliteLeaseHandle tryAcquireMaintenanceExclusion(Domain domain)
      throws IOException {
    Domain checkedDomain = Objects.requireNonNull(domain, "domain");
    return retainedLease(
        checkedDomain.controlPath(),
        SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
            checkedDomain.controlPath(),
            checkedDomain.magic(),
            SqliteCoordinationControlFiles.maintenanceLockPosition(),
            SqliteCoordinationControlFiles.maintenanceLockLength()));
  }

  /** Acquires one activity slot for a physical artifact, or fails only after every slot is held. */
  static ActivitySlot acquireActivitySlot(Domain domain) throws IOException {
    Domain checkedDomain = Objects.requireNonNull(domain, "domain");
    for (int slot = 0; slot < ACTIVITY_SLOT_COUNT; slot++) {
      @Nullable ActivitySlot slotHandle =
          activitySlot(
              SqliteCoordinationControlFiles.openOrCreateAndTryExclusiveLock(
                  checkedDomain.controlPath(),
                  checkedDomain.magic(),
                  SqliteCoordinationControlFiles.activitySlotPosition(slot),
                  1L));
      if (slotHandle != null) {
        return slotHandle;
      }
    }
    throw new IOException("No FinGrind object-coordination activity slot is available.");
  }

  /** Returns whether any activity slot for the physical artifact is presently held. */
  static boolean hasActiveSlot(Path existingArtifactPath) throws IOException {
    Domain domain = domainForExistingArtifact(existingArtifactPath);
    if (Files.notExists(domain.controlPath(), LinkOption.NOFOLLOW_LINKS)) {
      return false;
    }
    for (int slot = 0; slot < ACTIVITY_SLOT_COUNT; slot++) {
      try (SqliteCoordinationControlFiles.@Nullable LockedControlFile probe =
          SqliteCoordinationControlFiles.openExistingAndTryExclusiveLock(
              domain.controlPath(),
              domain.magic(),
              SqliteCoordinationControlFiles.activitySlotPosition(slot),
              1L)) {
        if (probe == null) {
          return true;
        }
      }
    }
    return false;
  }

  /** Returns the explicit physical identity without creating a coordination control. */
  static String physicalIdentity(Path existingArtifactPath) throws IOException {
    return SqliteCoordinationControlFiles.physicalObjectIdentity(existingArtifactPath);
  }

  /**
   * Installs an isolated coordination root for one in-process test scope.
   *
   * <p>Production processes always coordinate beneath the single owner-home root and cannot select
   * a divergent namespace through configuration.
   */
  static AutoCloseable installTestRoot(Path root) {
    Path checkedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    TEST_ROOT_LOCK.lock();
    try {
      TEST_ROOT_STACK.addLast(checkedRoot);
    } finally {
      TEST_ROOT_LOCK.unlock();
    }
    return () -> {
      TEST_ROOT_LOCK.lock();
      try {
        if (!checkedRoot.equals(TEST_ROOT_STACK.peekLast())) {
          throw new IllegalStateException(
              "The FinGrind object-coordination test root changed before its scope closed.");
        }
        TEST_ROOT_STACK.removeLast();
      } finally {
        TEST_ROOT_LOCK.unlock();
      }
    };
  }

  private static Path requirePrivateRoot() throws IOException {
    Path root = configuredRoot();
    requireNoRetiredNamespace(root);
    try {
      Path canonicalRoot =
          createOrValidatePrivateRoot(
              root,
              SqliteCoordinationControlFiles.isWindows(),
              SqliteWindowsCoordinationFfmTransport::createOrValidatePrivateRoot);
      requireNoRetiredObjectResidue(canonicalRoot);
      return canonicalRoot;
    } catch (PrivateOutputDirectory.Violation violation) {
      throw new IOException(
          "FinGrind could not establish its private object-coordination root at " + root + ".",
          violation);
    } catch (IOException exception) {
      throw new IOException(
          "FinGrind could not create or validate its private object-coordination root at "
              + root
              + ": "
              + Objects.requireNonNullElse(exception.getMessage(), "unspecified I/O failure"),
          exception);
    }
  }

  /** Selects the host-native private-root initializer without exposing a mutable root setting. */
  static Path createOrValidatePrivateRoot(
      Path root, boolean isWindows, PrivateRootInitializer windowsInitializer) throws IOException {
    Objects.requireNonNull(windowsInitializer, "windowsInitializer");
    return isWindows
        ? windowsInitializer.createOrValidate(root)
        : createOrValidatePrivatePosixRoot(root);
  }

  /** Creates or validates the POSIX-only private coordination root. */
  static Path createOrValidatePrivatePosixRoot(Path root) throws IOException {
    if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
      PrivateOutputDirectory.requireExistingOwnerOnly(root);
    } else {
      if (!SqliteBookFilesystemSupport.supportsPosix(root)) {
        throw new IOException(
            "FinGrind object coordination requires POSIX owner-only root creation or the Windows native control transport.");
      }
      PrivateOutputDirectory.createNewPosixOwnerOnlyDirectories(root);
    }
    return root.toRealPath(LinkOption.NOFOLLOW_LINKS).toAbsolutePath().normalize();
  }

  private static void requireNoRetiredNamespace(Path v4Root) throws IOException {
    for (String retiredRootName : List.of(RETIRED_V3_ROOT_DIRECTORY, RETIRED_V2_ROOT_DIRECTORY)) {
      Path retiredRoot = siblingNamespace(v4Root, retiredRootName);
      if (Files.exists(retiredRoot, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException(
            "Retired FinGrind object-coordination namespace blocks the v4 protocol: "
                + retiredRoot
                + ".");
      }
    }
  }

  private static Path siblingNamespace(Path v4Root, String defaultRetiredName) {
    Path checkedRoot = Objects.requireNonNull(v4Root, "v4Root");
    Path parent = Objects.requireNonNull(checkedRoot.getParent(), "v4Root parent");
    String leaf = Objects.requireNonNull(checkedRoot.getFileName(), "v4Root fileName").toString();
    if (leaf.endsWith("v4")) {
      return parent.resolve(
          leaf.substring(0, leaf.length() - 2)
              + defaultRetiredName.substring(defaultRetiredName.length() - 2));
    }
    return parent.resolve(defaultRetiredName);
  }

  private static void requireNoRetiredObjectResidue(Path root) throws IOException {
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        String name = Objects.requireNonNull(entry.getFileName(), "entry fileName").toString();
        if (name.startsWith(RETIRED_V3_OBJECT_PREFIX)
            || name.startsWith(RETIRED_V2_OBJECT_PREFIX)
            || RETIRED_V4_REGISTRY_FILE.equals(name)) {
          throw new IOException(
              "Retired FinGrind object-coordination state blocks the v4 protocol.");
        }
        if (!isCurrentObjectControlName(name)) {
          throw new IOException(
              "Unexpected state exists in the private FinGrind object-coordination root: "
                  + entry
                  + ".");
        }
      }
    }
  }

  private static boolean isCurrentObjectControlName(String fileName) {
    if (!fileName.startsWith(OBJECT_PREFIX) || !fileName.endsWith(CONTROL_SUFFIX)) {
      return false;
    }
    String digest =
        fileName.substring(OBJECT_PREFIX.length(), fileName.length() - CONTROL_SUFFIX.length());
    if (digest.length() != 64) {
      return false;
    }
    for (int index = 0; index < digest.length(); index++) {
      char character = digest.charAt(index);
      if (!(character >= '0' && character <= '9') && !(character >= 'a' && character <= 'f')) {
        return false;
      }
    }
    return true;
  }

  /** Resolves the process-wide root, with test scopes taking precedence over the user-home root. */
  static Path configuredRoot() throws IOException {
    TEST_ROOT_LOCK.lock();
    try {
      @Nullable Path testRoot = TEST_ROOT_STACK.peekLast();
      if (testRoot != null) {
        return testRoot;
      }
    } finally {
      TEST_ROOT_LOCK.unlock();
    }
    return userHomeRoot(System.getProperty("user.home"));
  }

  /** Derives the production root from one supplied user-home value. */
  static Path userHomeRoot(@Nullable String userHome) throws IOException {
    if (userHome == null || userHome.isBlank()) {
      throw new IOException("FinGrind cannot resolve a per-user object-coordination root.");
    }
    return Path.of(userHome).toAbsolutePath().normalize().resolve(DEFAULT_ROOT_DIRECTORY);
  }

  private static @Nullable SqliteLeaseHandle retainedLease(
      Path controlPath, SqliteCoordinationControlFiles.@Nullable LockedControlFile lockedControl) {
    return lockedControl == null ? null : new SqliteLeaseHandle(controlPath, lockedControl);
  }

  private static @Nullable ActivitySlot activitySlot(
      SqliteCoordinationControlFiles.@Nullable LockedControlFile lockedControl) {
    return lockedControl == null ? null : new ActivitySlot(lockedControl);
  }

  /** Initializes one private root using the platform-specific primitive selected by the caller. */
  @FunctionalInterface
  interface PrivateRootInitializer {
    Path createOrValidate(Path root) throws IOException;
  }

  /** Immutable v4 control domain for one explicit physical artifact identity. */
  static final class Domain {
    private final String objectIdentity;
    private final Path controlPath;
    private final byte[] magic;

    Domain(String objectIdentity, Path controlPath, byte[] magic) {
      this.objectIdentity = Objects.requireNonNull(objectIdentity, "objectIdentity");
      this.controlPath = Objects.requireNonNull(controlPath, "controlPath");
      this.magic = Objects.requireNonNull(magic, "magic").clone();
    }

    String objectIdentity() {
      return objectIdentity;
    }

    Path controlPath() {
      return controlPath;
    }

    byte[] magic() {
      return magic.clone();
    }
  }

  /** Opaque held object-coordination activity slot. */
  record ActivitySlot(SqliteCoordinationControlFiles.LockedControlFile control)
      implements AutoCloseable {
    ActivitySlot {
      Objects.requireNonNull(control, "control");
    }

    @Override
    public void close() throws IOException {
      control.close();
    }
  }
}
