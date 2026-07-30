package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

  /** Resolves and transfers one existing artifact's retained physical-object exclusion. */
  @FunctionalInterface
  interface ExistingArtifactObjectLeaseAcquirer {
    /** Acquires the physical-object exclusion for one existing normalized artifact. */
    SqliteThreadMaintenanceLeases.@org.jspecify.annotations.Nullable ObjectLeaseReference acquire(
        Path existingArtifactPath) throws IOException;

    /** Transfers both established ownership layers into one release-once artifact lease. */
    default SqliteHeldLease createHeldLease(
        Path artifactPath,
        SqliteThreadMaintenanceLeases.ObjectLeaseReference objectLease,
        SqliteOwnedHeldLease directoryLease) {
      return createHeldExistingArtifactLease(artifactPath, objectLease, directoryLease);
    }
  }

  /** Acquires one raw physical-object control before it becomes thread-retained ownership. */
  @FunctionalInterface
  interface ObjectMaintenanceExclusionAcquirer {
    /** Acquires one exclusive control for the supplied resolved physical-object domain. */
    @org.jspecify.annotations.Nullable SqliteLeaseHandle acquire(SqliteObjectCoordinationArtifacts.Domain domain) throws IOException;
  }

  static SqliteProtectedBookLeaseAcquisition acquire(
      Path normalizedArtifactPath, SqliteMaintenanceLeaseIntent leaseIntent) {
    return acquireWithAdmittedScopeAllowingExplicitSiblingAdmission(
        normalizedArtifactPath, leaseIntent, List.of(normalizedArtifactPath));
  }

  /**
   * Acquires one complete maintenance workflow scope before the workflow reads its source or
   * touches either final target.
   *
   * <p>Every member is validated and every source physical identity is distinct. Sources are
   * checked for native activity before any directory reservation is taken; targets are deliberately
   * not inspected before pair-publication admission classifies an occupied caller-owned leaf.
   * Canonical parent domains are then acquired in one deterministic order, while each existing
   * source also holds its global physical-object exclusion. A second source-activity pass closes
   * every source reference before reporting a race. The resulting scope deliberately keeps every
   * source member reference until pair admission exchanges only its target references for
   * prepared-publication references.
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
        normalizedArtifactPath,
        leaseIntent,
        admittedArtifactPaths,
        false,
        SqliteBookMaintenanceLease::acquireObjectLeaseReference);
  }

  /**
   * Acquires one exact admitted scope through the supplied physical-object exclusion boundary.
   *
   * <p>Package visibility lets ownership tests prove cleanup after an object-control refusal or
   * failure without weakening the production protocol boundary.
   */
  static SqliteProtectedBookLeaseAcquisition acquireWithAdmittedScope(
      Path normalizedArtifactPath,
      SqliteMaintenanceLeaseIntent leaseIntent,
      List<Path> admittedArtifactPaths,
      ExistingArtifactObjectLeaseAcquirer objectLeaseAcquirer) {
    return acquireWithAdmittedScope(
        normalizedArtifactPath, leaseIntent, admittedArtifactPaths, false, objectLeaseAcquirer);
  }

  private static SqliteProtectedBookLeaseAcquisition
      acquireWithAdmittedScopeAllowingExplicitSiblingAdmission(
          Path normalizedArtifactPath,
          SqliteMaintenanceLeaseIntent leaseIntent,
          List<Path> admittedArtifactPaths) {
    return acquireWithAdmittedScope(
        normalizedArtifactPath,
        leaseIntent,
        admittedArtifactPaths,
        true,
        SqliteBookMaintenanceLease::acquireObjectLeaseReference);
  }

  private static SqliteProtectedBookLeaseAcquisition acquireWithAdmittedScope(
      Path normalizedArtifactPath,
      SqliteMaintenanceLeaseIntent leaseIntent,
      List<Path> admittedArtifactPaths,
      boolean allowsExplicitSiblingAdmission,
      ExistingArtifactObjectLeaseAcquirer objectLeaseAcquirer) {
    Path checkedArtifactPath =
        Objects.requireNonNull(normalizedArtifactPath, "normalizedArtifactPath");
    Objects.requireNonNull(leaseIntent, "leaseIntent");
    List<Path> checkedAdmittedArtifacts =
        List.copyOf(Objects.requireNonNull(admittedArtifactPaths, "admittedArtifactPaths"));
    ExistingArtifactObjectLeaseAcquirer checkedObjectLeaseAcquirer =
        Objects.requireNonNull(objectLeaseAcquirer, "objectLeaseAcquirer");
    try {
      SqliteMaintenanceLeaseAuthority.validateArtifactForLeaseIntent(
          checkedArtifactPath, leaseIntent);
      SqliteProtectedBookLeaseAcquisition directoryAcquisition =
          SqliteMaintenanceDirectoryLeaseAcquirer.acquire(
              checkedArtifactPath,
              leaseIntent,
              checkedAdmittedArtifacts,
              allowsExplicitSiblingAdmission);
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
        objectLease = checkedObjectLeaseAcquirer.acquire(checkedArtifactPath);
        if (objectLease == null) {
          directoryLease.release();
          return new SqliteLeaseBusy(checkedArtifactPath);
        }
        return checkedObjectLeaseAcquirer.createHeldLease(
            checkedArtifactPath, objectLease, directoryLease);
      } catch (RuntimeException | Error failure) {
        List<SqliteRuntimeCloseSequence.CloseAction> closeActions = new ArrayList<>();
        if (objectLease != null) {
          closeActions.add(objectLease::release);
        }
        closeActions.add(directoryLease::release);
        SqliteRuntimeCloseSequence.closeAllPreservingFailure(closeActions, failure);
        throw failure;
      }
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to acquire one FinGrind maintenance lease.", exception);
    }
  }

  /**
   * Retains the global physical-object exclusion for one existing artifact.
   *
   * <p>The directory admission remains the exact caller-authority boundary. The object lease only
   * prevents another hard-link spelling from independently entering maintenance.
   */
  private static SqliteThreadMaintenanceLeases.@org.jspecify.annotations.Nullable ObjectLeaseReference
      acquireObjectLeaseReference(Path existingArtifactPath) throws IOException {
    return acquireObjectLeaseReference(
        existingArtifactPath, SqliteObjectCoordinationArtifacts::tryAcquireMaintenanceExclusion);
  }

  /** Resolves and retains one object exclusion through its exact raw-control boundary. */
  static SqliteThreadMaintenanceLeases.@org.jspecify.annotations.Nullable ObjectLeaseReference
      acquireObjectLeaseReference(
          Path existingArtifactPath,
          ObjectMaintenanceExclusionAcquirer maintenanceExclusionAcquirer)
          throws IOException {
    SqliteObjectCoordinationArtifacts.Domain domain =
        SqliteObjectCoordinationArtifacts.domainForExistingArtifact(existingArtifactPath);
    SqliteThreadMaintenanceLeases.ObjectLease existingLease =
        SqliteThreadMaintenanceLeases.objectLease(domain.objectIdentity());
    if (existingLease != null) {
      return existingLease.retain();
    }
    @org.jspecify.annotations.Nullable SqliteOwnedLeaseHandle leaseHandle =
        SqliteOwnedLeaseHandle.acquire(
            Objects.requireNonNull(maintenanceExclusionAcquirer, "maintenanceExclusionAcquirer")
                .acquire(domain));
    if (leaseHandle == null) {
      return null;
    }
    SqliteThreadMaintenanceLeases.ObjectLease newLease =
        new SqliteThreadMaintenanceLeases.ObjectLease(
            domain.objectIdentity(), leaseHandle.transfer());
    SqliteThreadMaintenanceLeases.retainObjectLease(newLease);
    return newLease.retain();
  }

  private static SqliteHeldLease createHeldExistingArtifactLease(
      Path artifactPath,
      SqliteThreadMaintenanceLeases.ObjectLeaseReference objectLease,
      SqliteOwnedHeldLease directoryLease) {
    SqliteThreadMaintenanceLeases.ObjectLeaseReference retainedObjectLease =
        Objects.requireNonNull(objectLease, "objectLease");
    SqliteOwnedHeldLease retainedDirectoryLease =
        Objects.requireNonNull(directoryLease, "directoryLease");
    return new SqliteHeldLease(
        Objects.requireNonNull(artifactPath, "artifactPath"),
        retainedObjectLease.objectIdentity(),
        () -> {
          try {
            retainedObjectLease.release();
          } finally {
            retainedDirectoryLease.release();
          }
        });
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
   * reference never exempts either exact target from its own publication-admission check.
   */
  static SqliteManagedTargetLeasePair acquireManagedTargetPair(
      Path normalizedBookTargetPath,
      Path normalizedSecretTargetPath,
      SqliteManagedTargetLeaseAcquirer targetLeaseAcquirer) {
    return SqliteManagedTargetLeaseCoordinator.acquire(
        normalizedBookTargetPath, normalizedSecretTargetPath, targetLeaseAcquirer);
  }
}
