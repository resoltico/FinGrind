package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Two-layer exclusive maintenance lease for protected-book artifacts.
 *
 * <p>Every held reference has exact artifact authority inside one canonical parent-directory
 * reservation. Existing artifacts additionally retain a global exclusion keyed by mandatory
 * physical object identity, so hard-link aliases in different parents converge. Workflow work in
 * one thread remains reference-counted only for its predeclared directory members; no workflow
 * directory reservation becomes ambient authority over siblings. A standalone caller may explicitly
 * retain a sibling through a separate standalone acquisition.
 */
final class SqliteBookMaintenanceLease {
  private SqliteBookMaintenanceLease() {}

  static SqliteProtectedBookLeaseAcquisition acquire(
      Path normalizedArtifactPath, SqliteMaintenanceLeaseIntent leaseIntent) {
    return acquireWithAdmittedScopeAllowingExplicitSiblingAdmission(
        normalizedArtifactPath, leaseIntent, List.of(normalizedArtifactPath));
  }

  /**
   * Acquires one complete maintenance workflow scope before the workflow reads its source or
   * touches either final target.
   *
   * <p>Every member is validated, every source physical identity is distinct, and every member is
   * checked for native activity before any directory reservation is taken. Canonical parent domains
   * are then acquired in one deterministic order, while each existing source also holds its global
   * physical-object exclusion. A second activity pass closes every reference before reporting a
   * race. The resulting scope deliberately keeps every source member reference until pair admission
   * exchanges only its target references for prepared-publication references.
   */
  static SqliteWorkflowScopeAcquisition acquireWorkflowScope(
      WorkflowSourceMembers normalizedSourceMembers,
      Path normalizedBookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole)
      throws IOException {
    return SqliteMaintenanceWorkflowScopeAcquirer.acquire(
        normalizedSourceMembers,
        normalizedBookTargetPath,
        bookTargetArtifactRole,
        normalizedSecretTargetPath,
        secretTargetArtifactRole);
  }

  /**
   * Acquires one exact artifact under an immutable set of same-directory artifacts admitted before
   * the directory reservation is first taken.
   *
   * <p>Admission scope is intentionally not broadened after acquisition. Existing artifacts also
   * require the global physical-object exclusion; a directory reservation alone never prevents a
   * hard-link spelling in a different parent from entering another workflow.
   */
  static SqliteProtectedBookLeaseAcquisition acquireWithAdmittedScope(
      Path normalizedArtifactPath,
      SqliteMaintenanceLeaseIntent leaseIntent,
      List<Path> admittedArtifactPaths) {
    return acquireWithAdmittedScope(
        normalizedArtifactPath, leaseIntent, admittedArtifactPaths, false);
  }

  private static SqliteProtectedBookLeaseAcquisition
      acquireWithAdmittedScopeAllowingExplicitSiblingAdmission(
          Path normalizedArtifactPath,
          SqliteMaintenanceLeaseIntent leaseIntent,
          List<Path> admittedArtifactPaths) {
    return acquireWithAdmittedScope(
        normalizedArtifactPath, leaseIntent, admittedArtifactPaths, true);
  }

  private static SqliteProtectedBookLeaseAcquisition acquireWithAdmittedScope(
      Path normalizedArtifactPath,
      SqliteMaintenanceLeaseIntent leaseIntent,
      List<Path> admittedArtifactPaths,
      boolean allowsExplicitSiblingAdmission) {
    Path checkedArtifactPath =
        Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    Objects.requireNonNull(leaseIntent, "leaseIntent");
    List<Path> checkedAdmittedArtifacts =
        List.copyOf(Objects.requireNonNull(admittedArtifactPaths, "admittedArtifactPaths"));
    try {
      SqliteMaintenanceLeaseAuthority.validateArtifactForLeaseIntent(
          checkedArtifactPath, leaseIntent);
      SqliteProtectedBookLeaseAcquisition directoryAcquisition =
          acquireDirectoryWithAdmittedScope(
              checkedArtifactPath, checkedAdmittedArtifacts, allowsExplicitSiblingAdmission);
      if (directoryAcquisition instanceof SqliteLeaseBusy busy) {
        return busy;
      }
      SqliteOwnedHeldLease directoryLease = SqliteOwnedHeldLease.acquire(directoryAcquisition);
      if (leaseIntent != SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT) {
        return directoryLease.transfer();
      }
      SqliteThreadMaintenanceLeases.@org.jspecify.annotations.Nullable ObjectLeaseReference
          objectLease = null;
      try {
        objectLease = acquireObjectLeaseReference(checkedArtifactPath);
        if (objectLease == null) {
          directoryLease.release();
          return new SqliteLeaseBusy(checkedArtifactPath);
        }
        SqliteThreadMaintenanceLeases.ObjectLeaseReference retainedObjectLease = objectLease;
        return new SqliteHeldLease(
            checkedArtifactPath,
            retainedObjectLease.objectIdentity(),
            () -> {
              try {
                retainedObjectLease.release();
              } finally {
                directoryLease.release();
              }
            });
      } catch (RuntimeException | Error failure) {
        if (objectLease != null) {
          try {
            objectLease.release();
          } catch (RuntimeException | Error closeFailure) {
            failure.addSuppressed(closeFailure);
          }
        }
        try {
          directoryLease.release();
        } catch (RuntimeException | Error closeFailure) {
          failure.addSuppressed(closeFailure);
        }
        throw failure;
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire one FinGrind maintenance lease.", exception);
    }
  }

  /** Acquires one exact admitted directory reference without widening a workflow's authority. */
  private static SqliteProtectedBookLeaseAcquisition acquireDirectoryWithAdmittedScope(
      Path checkedArtifactPath,
      List<Path> checkedAdmittedArtifacts,
      boolean allowsExplicitSiblingAdmission)
      throws IOException {
    Path directoryDomain =
        SqliteMaintenanceLeaseAuthority.canonicalDirectoryDomain(checkedArtifactPath);
    Set<String> admittedArtifactKeys =
        admittedArtifactKeys(checkedArtifactPath, directoryDomain, checkedAdmittedArtifacts);
    SqliteThreadMaintenanceLeases.DirectoryLease ownedLease =
        SqliteThreadMaintenanceLeases.directoryLease(directoryDomain);
    if (ownedLease != null) {
      return retainUnderOwnedDirectoryLease(
          ownedLease, checkedArtifactPath, allowsExplicitSiblingAdmission);
    }
    @org.jspecify.annotations.Nullable SqliteOwnedLeaseHandle leaseHandle =
        SqliteOwnedLeaseHandle.acquire(SqliteMaintenanceLeaseArtifacts.acquire(directoryDomain));
    if (leaseHandle == null) {
      return new SqliteLeaseBusy(checkedArtifactPath);
    }
    if (SqliteMaintenanceLeaseAuthority.hasBlockingActivity(checkedArtifactPath)) {
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
      boolean allowsExplicitSiblingAdmission) {
    if (ownedLease.admits(checkedArtifactPath)) {
      if (!ownedLease.owns(checkedArtifactPath)
          && SqliteMaintenanceLeaseAuthority.hasBlockingActivity(checkedArtifactPath)) {
        return new SqliteLeaseBusy(checkedArtifactPath);
      }
      return ownedLease.retain(checkedArtifactPath);
    }
    if (!allowsExplicitSiblingAdmission
        || !ownedLease.permitsExplicitSiblingAdmission(checkedArtifactPath)
        || SqliteMaintenanceLeaseAuthority.hasBlockingActivity(checkedArtifactPath)) {
      return new SqliteLeaseBusy(checkedArtifactPath);
    }
    ownedLease.admitExplicitSibling(checkedArtifactPath);
    return ownedLease.retain(checkedArtifactPath);
  }

  /**
   * Retains the global physical-object exclusion for one existing artifact.
   *
   * <p>The directory admission remains the exact caller-authority boundary. The object lease only
   * prevents another hard-link spelling from independently entering maintenance.
   */
  private static SqliteThreadMaintenanceLeases.@org.jspecify.annotations.Nullable ObjectLeaseReference
      acquireObjectLeaseReference(Path existingArtifactPath) throws IOException {
    SqliteObjectCoordinationArtifacts.Domain domain =
        SqliteObjectCoordinationArtifacts.domainForExistingArtifact(existingArtifactPath);
    SqliteThreadMaintenanceLeases.ObjectLease existingLease =
        SqliteThreadMaintenanceLeases.objectLease(domain.objectIdentity());
    if (existingLease != null) {
      return existingLease.retain();
    }
    @org.jspecify.annotations.Nullable SqliteOwnedLeaseHandle leaseHandle =
        SqliteOwnedLeaseHandle.acquire(
            SqliteObjectCoordinationArtifacts.tryAcquireMaintenanceExclusion(domain));
    if (leaseHandle == null) {
      return null;
    }
    SqliteThreadMaintenanceLeases.ObjectLease newLease =
        new SqliteThreadMaintenanceLeases.ObjectLease(
            domain.objectIdentity(), leaseHandle.transfer());
    SqliteThreadMaintenanceLeases.retainObjectLease(newLease);
    return newLease.retain();
  }

  /**
   * Acquires the two managed-target directory domains in one deterministic total order.
   *
   * <p>The order is the canonical real parent directory, then the normalized target path. A
   * same-parent pair deliberately acquires one physical directory lease and retains it for both
   * members. If any later acquisition is busy, every earlier reference acquired by this call is
   * released before the busy result escapes.
   */
  static SqliteManagedTargetLeasePair acquireManagedTargetPair(
      Path normalizedBookTargetPath, Path normalizedSecretTargetPath) {
    return SqliteManagedTargetLeaseCoordinator.acquire(
        normalizedBookTargetPath, normalizedSecretTargetPath);
  }

  /**
   * Acquires one managed target pair through an injectable acquisition seam for race testing.
   *
   * <p>Every target is checked before the first lease is acquired and once again after both
   * references are held. This is defense in depth for same-parent ordering: acquiring a directory
   * reference never exempts either exact artifact from its own native-activity check.
   */
  static SqliteManagedTargetLeasePair acquireManagedTargetPair(
      Path normalizedBookTargetPath,
      Path normalizedSecretTargetPath,
      SqliteManagedTargetLeaseAcquirer targetLeaseAcquirer) {
    return SqliteManagedTargetLeaseCoordinator.acquire(
        normalizedBookTargetPath, normalizedSecretTargetPath, targetLeaseAcquirer);
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
