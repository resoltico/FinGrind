package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.maintenance.MaintenanceFailure;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;

/** Maps protected-book staging failures to their public decision and retained-evidence policy. */
final class SqliteProtectedBookStagingFailure {
  private SqliteProtectedBookStagingFailure() {}

  static <T> MaintenanceDecision<T> at(
      Path artifactPath, String argumentName, SqliteProtectedBookStagingCheckpoint checkpoint) {
    return at(artifactPath, argumentName, checkpoint.failureMessage());
  }

  static <T> MaintenanceDecision<T> at(
      Path artifactPath, String argumentName, String failureMessage) {
    return MaintenanceDecision.failed(
        new MaintenanceFailure(
            ContractErrors.Descriptor.STORAGE_RUNTIME_FAILURE,
            failureMessage,
            "Inspect the selected filesystem path and retry after resolving the underlying storage problem.",
            argumentName,
            ContractFailurePaths.primary(artifactPath),
            null));
  }

  static void releaseAllRetained(
      @Nullable SqliteOwnedStagedArtifact first, @Nullable SqliteOwnedStagedArtifact second) {
    SqliteOwnedStagedArtifact.releaseAllRetained(first, second);
  }
}
