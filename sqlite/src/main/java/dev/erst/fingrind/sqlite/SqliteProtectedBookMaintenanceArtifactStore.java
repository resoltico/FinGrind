package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMember;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore.WorkflowSourceMembers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Filesystem-facing protected-book maintenance SPI operations shared by SQLite maintenance stores.
 */
abstract class SqliteProtectedBookMaintenanceArtifactStore
    implements ProtectedBookMaintenanceStore {
  /** Acquires the full protected-book maintenance scope before filesystem mutation begins. */
  @FunctionalInterface
  interface WorkflowScopeAcquirer {
    /** Acquires the declared source members and final targets as one immutable workflow scope. */
    SqliteWorkflowScopeAcquisition acquire(
        WorkflowSourceMembers sourceMembers,
        Path bookTargetPath,
        ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
        Path secretTargetPath,
        ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole)
        throws IOException;
  }

  private final WorkflowScopeAcquirer workflowScopeAcquirer;

  SqliteProtectedBookMaintenanceArtifactStore(WorkflowScopeAcquirer workflowScopeAcquirer) {
    this.workflowScopeAcquirer =
        Objects.requireNonNull(workflowScopeAcquirer, "workflowScopeAcquirer");
  }

  @Override
  public Path normalizeOptionalInspectionArtifact(
      Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole) {
    Path requestedPath = Objects.requireNonNull(path, "path").toAbsolutePath();
    ProtectedBookMaintenanceArtifactRole checkedArtifactRole =
        Objects.requireNonNull(artifactRole, "artifactRole");
    if (!isOptionalInspectionRole(checkedArtifactRole)) {
      throw new IllegalArgumentException(
          "Only a live-book inspection artifact can use optional maintenance normalization.");
    }
    try {
      return SqliteBookMaintenanceFiles.normalizeOptionalArtifact(requestedPath, argumentName);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(checkedArtifactRole, exception);
    }
  }

  @Override
  public Path normalizeFinalTarget(
      Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole) {
    Path requestedPath = Objects.requireNonNull(path, "path").toAbsolutePath();
    ProtectedBookMaintenanceArtifactRole checkedArtifactRole =
        Objects.requireNonNull(artifactRole, "artifactRole");
    try {
      ensureFinalTargetParent(requestedPath, checkedArtifactRole);
      return SqliteBookMaintenanceFiles.normalizeOptionalArtifact(requestedPath, argumentName);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(checkedArtifactRole, exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to prepare the protected-book maintenance target parent "
              + SqliteMachinePaths.absoluteValue(requestedPath)
              + ".",
          exception);
    }
  }

  private static void ensureFinalTargetParent(
      Path requestedPath, ProtectedBookMaintenanceArtifactRole artifactRole) throws IOException {
    finalTargetParentAdmission(artifactRole).ensure(requestedPath);
  }

  private static FinalTargetParentAdmission finalTargetParentAdmission(
      ProtectedBookMaintenanceArtifactRole artifactRole) {
    return switch (Objects.requireNonNull(artifactRole, "artifactRole")) {
      case LIVE_BOOK, BACKUP_TARGET, RESTORED_TARGET ->
          SqliteBookFileSecurity::ensureSecureParentDirectory;
      case BACKUP_KEY_TARGET, NEW_BOOK_KEY_TARGET ->
          SqliteBookKeyFileSecurity::ensureSecureParentDirectory;
      case LIVE_BOOK_KEY_SOURCE, BACKUP_SOURCE, BACKUP_KEY_SOURCE ->
          requestedPath -> {
            throw new IllegalArgumentException(
                "A protected-book maintenance source cannot use final-target normalization.");
          };
    };
  }

  /** Validates the secure parent needed for one final protected-book maintenance target. */
  @FunctionalInterface
  private interface FinalTargetParentAdmission {
    /** Validates that the selected final target can be admitted beneath its secure parent. */
    void ensure(Path requestedPath) throws IOException;
  }

  @Override
  public Path normalizeExistingSource(
      Path path, String argumentName, ProtectedBookMaintenanceArtifactRole artifactRole) {
    Path requestedPath = Objects.requireNonNull(path, "path").toAbsolutePath();
    ProtectedBookMaintenanceArtifactRole checkedArtifactRole =
        Objects.requireNonNull(artifactRole, "artifactRole");
    if (!isSourceRole(checkedArtifactRole)) {
      throw new IllegalArgumentException(
          "Only a protected-book maintenance source can require an existing source artifact.");
    }
    try {
      return SqliteBookMaintenanceFiles.normalizeExistingSource(requestedPath, argumentName);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(checkedArtifactRole, exception);
    }
  }

  @Override
  public List<Path> blockingArtifactsForBook(Path normalizedBookPath) {
    return SqliteBookMaintenanceFiles.blockingArtifactsForBook(normalizedBookPath);
  }

  @Override
  public List<Path> blockingArtifactsForBackupSource(Path normalizedBackupFilePath) {
    return SqliteBookMaintenanceFiles.blockingArtifactsForBackupSource(normalizedBackupFilePath);
  }

  @Override
  public LeaseAcquisition acquireExistingArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    return acquireLease(
        normalizedArtifactPath, artifactRole, SqliteMaintenanceLeaseIntent.EXISTING_ARTIFACT);
  }

  @Override
  public LeaseAcquisition acquireManagedArtifactLease(
      Path normalizedArtifactPath, ProtectedBookMaintenanceArtifactRole artifactRole) {
    return acquireLease(
        normalizedArtifactPath, artifactRole, SqliteMaintenanceLeaseIntent.MANAGED_TARGET);
  }

  /**
   * Acquires every immutable member of one source-and-pair workflow scope before any workflow phase
   * can inspect the source or mutate final-target preparation state.
   */
  final SqliteWorkflowScopeAcquisition acquireWorkflowLeaseScope(
      WorkflowSourceMembers normalizedSourceMembers,
      Path normalizedBookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      Path normalizedSecretTargetPath,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole) {
    WorkflowSourceMembers checkedSourceMembers =
        Objects.requireNonNull(normalizedSourceMembers, "normalizedSourceMembers");
    Path checkedBookTarget =
        Objects.requireNonNull(normalizedBookTargetPath, "normalizedBookTargetPath");
    Path checkedSecretTarget =
        Objects.requireNonNull(normalizedSecretTargetPath, "normalizedSecretTargetPath");
    ProtectedBookMaintenanceArtifactRole checkedBookRole =
        Objects.requireNonNull(bookTargetArtifactRole, "bookTargetArtifactRole");
    ProtectedBookMaintenanceArtifactRole checkedSecretRole =
        Objects.requireNonNull(secretTargetArtifactRole, "secretTargetArtifactRole");
    try {
      return workflowScopeAcquirer.acquire(
          checkedSourceMembers,
          checkedBookTarget,
          checkedBookRole,
          checkedSecretTarget,
          checkedSecretRole);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(
          roleForWorkflowMember(
              exception.requestedPath(),
              checkedSourceMembers,
              checkedBookTarget,
              checkedBookRole,
              checkedSecretRole),
          exception);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Failed to establish distinct physical identities for the protected-book maintenance sources.",
          exception);
    }
  }

  protected static SqliteVerifiedBook requireVerifiedBook(VerifiedBook verifiedBook) {
    Objects.requireNonNull(verifiedBook, "verifiedBook");
    if (verifiedBook instanceof SqliteVerifiedBook sqliteVerifiedBook) {
      return sqliteVerifiedBook;
    }
    throw new IllegalArgumentException(
        "The SQLite maintenance store requires one verified SQLite book handle.");
  }

  protected static ProtectedBookMaintenanceRejectionException maintenanceRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      SqliteCallerPathContractException exception) {
    return new ProtectedBookMaintenanceRejectionException(
        SqliteCallerPathFailureMapper.maintenanceRejection(artifactRole, exception));
  }

  private static LeaseAcquisition acquireLease(
      Path normalizedArtifactPath,
      ProtectedBookMaintenanceArtifactRole artifactRole,
      SqliteMaintenanceLeaseIntent leaseIntent) {
    try {
      return switch (SqliteBookMaintenanceLease.acquire(normalizedArtifactPath, leaseIntent)) {
        case SqliteHeldLease heldLease -> heldLease;
        case SqliteLeaseBusy leaseBusy -> new LeaseBusy(leaseBusy.artifactPath());
      };
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(artifactRole, exception);
    }
  }

  private static ProtectedBookMaintenanceArtifactRole roleFor(
      Path artifactPath,
      Path bookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretArtifactRole) {
    return SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
            Objects.requireNonNull(artifactPath, "artifactPath"),
            Objects.requireNonNull(bookTargetPath, "bookTargetPath"))
        ? Objects.requireNonNull(bookArtifactRole, "bookArtifactRole")
        : Objects.requireNonNull(secretArtifactRole, "secretArtifactRole");
  }

  private static boolean isSourceRole(ProtectedBookMaintenanceArtifactRole artifactRole) {
    return switch (Objects.requireNonNull(artifactRole, "artifactRole")) {
      case LIVE_BOOK, LIVE_BOOK_KEY_SOURCE, BACKUP_SOURCE, BACKUP_KEY_SOURCE -> true;
      case BACKUP_TARGET, BACKUP_KEY_TARGET, RESTORED_TARGET, NEW_BOOK_KEY_TARGET -> false;
    };
  }

  private static boolean isOptionalInspectionRole(
      ProtectedBookMaintenanceArtifactRole artifactRole) {
    return switch (Objects.requireNonNull(artifactRole, "artifactRole")) {
      case LIVE_BOOK, LIVE_BOOK_KEY_SOURCE -> true;
      case BACKUP_SOURCE,
          BACKUP_KEY_SOURCE,
          BACKUP_TARGET,
          BACKUP_KEY_TARGET,
          RESTORED_TARGET,
          NEW_BOOK_KEY_TARGET ->
          false;
    };
  }

  private static ProtectedBookMaintenanceArtifactRole roleForWorkflowMember(
      Path artifactPath,
      WorkflowSourceMembers sourceMembers,
      Path bookTargetPath,
      ProtectedBookMaintenanceArtifactRole bookTargetArtifactRole,
      ProtectedBookMaintenanceArtifactRole secretTargetArtifactRole) {
    Path checkedArtifactPath = Objects.requireNonNull(artifactPath, "artifactPath");
    for (WorkflowSourceMember sourceMember :
        Objects.requireNonNull(sourceMembers, "sourceMembers").members()) {
      if (SqliteProtectedBookPathIdentity.sameNormalizedSpelling(
          checkedArtifactPath, sourceMember.artifactPath())) {
        return sourceMember.artifactRole();
      }
    }
    return roleFor(
        checkedArtifactPath,
        Objects.requireNonNull(bookTargetPath, "bookTargetPath"),
        Objects.requireNonNull(bookTargetArtifactRole, "bookTargetArtifactRole"),
        Objects.requireNonNull(secretTargetArtifactRole, "secretTargetArtifactRole"));
  }
}
