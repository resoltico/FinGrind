package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Per-thread retained ownership state for directory and physical-object maintenance exclusions. */
final class SqliteThreadMaintenanceLeases {
  private static final ThreadLocal<Map<String, DirectoryLease>> DIRECTORY_LEASES =
      ThreadLocal.withInitial(ConcurrentHashMap::new);
  private static final ThreadLocal<Map<String, ObjectLease>> OBJECT_LEASES =
      ThreadLocal.withInitial(ConcurrentHashMap::new);

  private SqliteThreadMaintenanceLeases() {}

  static @org.jspecify.annotations.Nullable DirectoryLease directoryLease(Path directoryDomain) {
    return DIRECTORY_LEASES.get().get(directoryKey(directoryDomain));
  }

  static void retainDirectoryLease(DirectoryLease lease) {
    DirectoryLease checkedLease = Objects.requireNonNull(lease, "lease");
    DIRECTORY_LEASES.get().put(directoryKey(checkedLease.directoryDomain), checkedLease);
  }

  static @org.jspecify.annotations.Nullable ObjectLease objectLease(String objectIdentity) {
    return OBJECT_LEASES.get().get(Objects.requireNonNull(objectIdentity, "objectIdentity"));
  }

  /**
   * Retains this thread's existing physical-object exclusion for one nested native connection.
   *
   * <p>A maintenance workflow may inspect its locked source through SQLite before it releases the
   * workflow scope. The retained reference keeps the physical exclusion in force until that nested
   * connection closes; another thread cannot inherit this authority.
   */
  static @org.jspecify.annotations.Nullable ObjectLeaseReference retainCurrentThreadObjectLease(
      String objectIdentity) {
    ObjectLease existingLease = objectLease(objectIdentity);
    return existingLease == null ? null : existingLease.retain();
  }

  static void retainObjectLease(ObjectLease lease) {
    ObjectLease checkedLease = Objects.requireNonNull(lease, "lease");
    OBJECT_LEASES.get().put(checkedLease.objectIdentity, checkedLease);
  }

  static String directoryKey(Path canonicalDirectoryDomain) {
    return SqliteProtectedBookPathIdentity.normalizedSpelling(canonicalDirectoryDomain);
  }

  /** Ref-counted same-thread reservation of one admitted canonical directory. */
  static final class DirectoryLease {
    private final Path directoryDomain;
    private final SqliteLeaseHandle leaseHandle;
    private final Set<String> admittedArtifactKeys;
    private final boolean permitsExplicitSiblingAdmission;
    private final Map<String, Integer> artifactReferences = new ConcurrentHashMap<>();
    private int references;

    DirectoryLease(
        Path directoryDomain,
        SqliteLeaseHandle leaseHandle,
        Set<String> admittedArtifactKeys,
        boolean permitsExplicitSiblingAdmission) {
      this.directoryDomain = Objects.requireNonNull(directoryDomain, "directoryDomain");
      this.leaseHandle = Objects.requireNonNull(leaseHandle, "leaseHandle");
      this.admittedArtifactKeys = ConcurrentHashMap.newKeySet();
      this.admittedArtifactKeys.addAll(admittedArtifactKeys);
      this.permitsExplicitSiblingAdmission = permitsExplicitSiblingAdmission;
    }

    SqliteHeldLease retain(Path artifactPath) {
      Path checkedArtifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
      if (!admits(checkedArtifactPath)) {
        throw new IllegalStateException(
            "The FinGrind maintenance directory lease cannot retain an artifact outside its admitted scope.");
      }
      references++;
      artifactReferences.merge(artifactKey(checkedArtifactPath), 1, Integer::sum);
      return new SqliteHeldLease(checkedArtifactPath, () -> release(checkedArtifactPath));
    }

    boolean owns(Path artifactPath) {
      return artifactReferences.containsKey(artifactKey(artifactPath));
    }

    boolean admits(Path artifactPath) {
      return admittedArtifactKeys.contains(artifactKey(artifactPath));
    }

    boolean permitsExplicitSiblingAdmission(Path artifactPath) {
      return permitsExplicitSiblingAdmission && !admits(artifactPath);
    }

    void admitExplicitSibling(Path artifactPath) {
      if (!permitsExplicitSiblingAdmission(artifactPath)) {
        throw new IllegalStateException(
            "The FinGrind maintenance directory lease cannot broaden this workflow's admitted scope.");
      }
      admittedArtifactKeys.add(artifactKey(artifactPath));
    }

    private void release(Path artifactPath) {
      if (references <= 0) {
        throw new IllegalStateException(
            "The FinGrind maintenance directory lease was over-released.");
      }
      String artifactKey = artifactKey(artifactPath);
      Integer artifactReferenceCount = artifactReferences.get(artifactKey);
      if (artifactReferenceCount == null || artifactReferenceCount <= 0) {
        throw new IllegalStateException(
            "The FinGrind maintenance artifact lease ownership changed unexpectedly.");
      }
      references--;
      if (artifactReferenceCount == 1) {
        artifactReferences.remove(artifactKey);
      } else {
        artifactReferences.put(artifactKey, artifactReferenceCount - 1);
      }
      if (references != 0) {
        return;
      }
      if (!artifactReferences.isEmpty()) {
        throw new IllegalStateException(
            "The FinGrind maintenance directory lease retained unexpected artifact references.");
      }
      Map<String, DirectoryLease> ownedLeases = DIRECTORY_LEASES.get();
      if (!ownedLeases.remove(directoryKey(directoryDomain), this)) {
        throw new IllegalStateException(
            "The FinGrind maintenance directory lease ownership changed unexpectedly.");
      }
      if (ownedLeases.isEmpty()) {
        DIRECTORY_LEASES.remove();
      }
      leaseHandle.close();
    }

    static String artifactKey(Path artifactPath) {
      return SqliteProtectedBookPathIdentity.normalizedSpelling(
          Objects.requireNonNull(artifactPath, "artifactPath"));
    }
  }

  /** Ref-counted same-thread exclusion for one physical object identity. */
  static final class ObjectLease {
    private final String objectIdentity;
    private final SqliteLeaseHandle leaseHandle;
    private int references;

    ObjectLease(String objectIdentity, SqliteLeaseHandle leaseHandle) {
      this.objectIdentity = Objects.requireNonNull(objectIdentity, "objectIdentity");
      this.leaseHandle = Objects.requireNonNull(leaseHandle, "leaseHandle");
    }

    ObjectLeaseReference retain() {
      references++;
      return new ObjectLeaseReference(this);
    }

    private void release() {
      if (references <= 0) {
        throw new IllegalStateException("The FinGrind maintenance object lease was over-released.");
      }
      references--;
      if (references != 0) {
        return;
      }
      Map<String, ObjectLease> ownedLeases = OBJECT_LEASES.get();
      if (!ownedLeases.remove(objectIdentity, this)) {
        throw new IllegalStateException(
            "The FinGrind maintenance object lease ownership changed unexpectedly.");
      }
      if (ownedLeases.isEmpty()) {
        OBJECT_LEASES.remove();
      }
      leaseHandle.close();
    }
  }

  /** One release-once reference to one same-thread physical-object exclusion. */
  static final class ObjectLeaseReference {
    private final ObjectLease owner;
    private boolean released;

    ObjectLeaseReference(ObjectLease owner) {
      this.owner = Objects.requireNonNull(owner, "owner");
    }

    String objectIdentity() {
      return owner.objectIdentity;
    }

    void release() {
      if (!released) {
        released = true;
        owner.release();
      }
    }
  }
}
