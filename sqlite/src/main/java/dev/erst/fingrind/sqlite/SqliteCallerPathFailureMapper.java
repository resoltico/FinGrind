package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookLiveAccessPathFailures;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceArtifactRole;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenancePathFailure;
import dev.erst.fingrind.executor.maintenance.ProtectedBookMaintenanceRejection;

/** Maps local caller path violations onto public failures and maintenance refusals. */
final class SqliteCallerPathFailureMapper {
  private SqliteCallerPathFailureMapper() {}

  static ContractFailure invalidBookFilePath(SqliteCallerPathContractException exception) {
    return ProtectedBookLiveAccessPathFailures.bookFile(
        exception.requestedPath(), toMaintenancePathFailure(exception.pathFailure()), null);
  }

  static ContractFailure invalidBookKeyFile(SqliteCallerPathContractException exception) {
    return ProtectedBookLiveAccessPathFailures.bookKeyFile(
        exception.requestedPath(), toMaintenancePathFailure(exception.pathFailure()), null);
  }

  static ProtectedBookMaintenanceRejection.ArtifactPathInvalid maintenanceRejection(
      ProtectedBookMaintenanceArtifactRole artifactRole,
      SqliteCallerPathContractException exception) {
    return new ProtectedBookMaintenanceRejection.ArtifactPathInvalid(
        artifactRole, exception.requestedPath(), toMaintenancePathFailure(exception.pathFailure()));
  }

  private static ProtectedBookMaintenancePathFailure toMaintenancePathFailure(
      SqliteCallerPathFailure pathFailure) {
    return pathFailure.maintenanceFailure();
  }
}
