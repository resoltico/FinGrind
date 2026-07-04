package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejectionException;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Filesystem-facing protected-book maintenance SPI operations shared by SQLite maintenance stores.
 */
abstract class SqliteProtectedBookMaintenanceArtifactStore
    implements ProtectedBookMaintenanceStore {
  @Override
  public Path normalize(Path path, String argumentName) {
    return SqliteBookMaintenanceFiles.normalize(path, argumentName);
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

  @Override
  public StagedBookReplacement stageReplacement(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    try {
      return SqliteStagedBookReplacement.create(normalizedSourceBookPath, normalizedTargetBookPath);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(ProtectedBookMaintenanceArtifactRole.RESTORED_TARGET, exception);
    }
  }

  @Override
  public List<Path> staleRollbackArtifacts(Path normalizedBookPath) {
    try {
      return SqliteRekeyRollbackFile.staleRollbackArtifacts(normalizedBookPath);
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(
          "Failed to inspect FinGrind SQLite rollback artifacts beside "
              + dev.erst.fingrind.contract.runtime.PublicPathHint.fromPath(normalizedBookPath)
                  .value()
              + ".",
          exception);
    }
  }

  @Override
  public boolean isRollbackArtifactForBook(
      Path normalizedBookPath, Path normalizedRollbackArtifactPath) {
    return SqliteRekeyRollbackFile.isRollbackArtifactForBook(
        normalizedBookPath, normalizedRollbackArtifactPath);
  }

  @Override
  public StagedRollbackArtifactDeletion stageRollbackArtifactDeletion(
      Path normalizedRollbackArtifactPath) {
    try {
      SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(
          normalizedRollbackArtifactPath);
      return SqliteStagedRollbackDeletion.create(normalizedRollbackArtifactPath);
    } catch (SqliteCallerPathContractException exception) {
      throw maintenanceRejection(ProtectedBookMaintenanceArtifactRole.ROLLBACK_ARTIFACT, exception);
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
}
