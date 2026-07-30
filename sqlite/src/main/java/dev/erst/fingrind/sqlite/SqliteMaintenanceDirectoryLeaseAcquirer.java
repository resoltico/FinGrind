package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Acquires exact artifact references beneath one canonical maintenance-directory lease. */
final class SqliteMaintenanceDirectoryLeaseAcquirer {
  private SqliteMaintenanceDirectoryLeaseAcquirer() {}

  /** Acquires one exact admitted directory reference without widening a workflow's authority. */
  static SqliteProtectedBookLeaseAcquisition acquire(
      Path checkedArtifactPath,
      SqliteMaintenanceLeaseIntent leaseIntent,
      List<Path> checkedAdmittedArtifacts,
      boolean allowsExplicitSiblingAdmission)
      throws IOException {
    SqliteMaintenanceLeaseIntent checkedLeaseIntent =
        Objects.requireNonNull(leaseIntent, "leaseIntent");
    Path directoryDomain =
        SqliteMaintenanceLeaseAuthority.canonicalDirectoryDomain(checkedArtifactPath);
    Set<String> admittedArtifactKeys =
        admittedArtifactKeys(checkedArtifactPath, directoryDomain, checkedAdmittedArtifacts);
    SqliteThreadMaintenanceLeases.DirectoryLease ownedLease =
        SqliteThreadMaintenanceLeases.directoryLease(directoryDomain);
    if (ownedLease != null) {
      return retainUnderOwnedDirectoryLease(
          ownedLease,
          checkedArtifactPath,
          allowsExplicitSiblingAdmission,
          checkedLeaseIntent.requiresNativeActivityCheck());
    }
    @org.jspecify.annotations.Nullable SqliteOwnedLeaseHandle leaseHandle =
        SqliteOwnedLeaseHandle.acquire(SqliteMaintenanceLeaseArtifacts.acquire(directoryDomain));
    if (leaseHandle == null) {
      return new SqliteLeaseBusy(checkedArtifactPath);
    }
    if (checkedLeaseIntent.requiresNativeActivityCheck()
        && SqliteMaintenanceLeaseAuthority.hasBlockingActivity(checkedArtifactPath)) {
      leaseHandle.release();
      return new SqliteLeaseBusy(checkedArtifactPath);
    }
    SqliteThreadMaintenanceLeases.DirectoryLease newOwnedLease =
        new SqliteThreadMaintenanceLeases.DirectoryLease(
            directoryDomain,
            leaseHandle.transfer(),
            admittedArtifactKeys,
            allowsExplicitSiblingAdmission);
    SqliteThreadMaintenanceLeases.retainDirectoryLease(newOwnedLease);
    return newOwnedLease.retain(checkedArtifactPath);
  }

  /** Retains one exact artifact without granting ambient sibling authority. */
  private static SqliteProtectedBookLeaseAcquisition retainUnderOwnedDirectoryLease(
      SqliteThreadMaintenanceLeases.DirectoryLease ownedLease,
      Path checkedArtifactPath,
      boolean allowsExplicitSiblingAdmission,
      boolean requiresNativeActivityCheck) {
    if (ownedLease.admits(checkedArtifactPath)) {
      if (!ownedLease.owns(checkedArtifactPath)
          && requiresNativeActivityCheck
          && SqliteMaintenanceLeaseAuthority.hasBlockingActivity(checkedArtifactPath)) {
        return new SqliteLeaseBusy(checkedArtifactPath);
      }
      return ownedLease.retain(checkedArtifactPath);
    }
    if (!allowsExplicitSiblingAdmission
        || !ownedLease.permitsExplicitSiblingAdmission(checkedArtifactPath)
        || (requiresNativeActivityCheck
            && SqliteMaintenanceLeaseAuthority.hasBlockingActivity(checkedArtifactPath))) {
      return new SqliteLeaseBusy(checkedArtifactPath);
    }
    ownedLease.admitExplicitSibling(checkedArtifactPath);
    return ownedLease.retain(checkedArtifactPath);
  }

  private static Set<String> admittedArtifactKeys(
      Path artifactPath, Path directoryDomain, List<Path> admittedArtifactPaths)
      throws IOException {
    Set<String> keys = new HashSet<>();
    for (Path admittedArtifactPath : admittedArtifactPaths) {
      Path checkedAdmittedPath =
          Objects.requireNonNull(admittedArtifactPath, "admittedArtifactPath");
      if (!SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
          SqliteMaintenanceLeaseAuthority.canonicalDirectoryDomain(checkedAdmittedPath),
          directoryDomain)) {
        throw new IllegalArgumentException(
            "One FinGrind maintenance lease admission scope crossed directory domains: "
                + artifactPath
                + ".");
      }
      keys.add(SqliteThreadMaintenanceLeases.DirectoryLease.artifactKey(checkedAdmittedPath));
    }
    if (!keys.contains(SqliteThreadMaintenanceLeases.DirectoryLease.artifactKey(artifactPath))) {
      throw new IllegalArgumentException(
          "One FinGrind maintenance lease admission scope omitted its acquired artifact: "
              + artifactPath
              + ".");
    }
    return Set.copyOf(keys);
  }
}
