package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.StagedRollbackArtifactDeletion;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Commit-only rollback-artifact deletion that leaves the recovery object untouched until publish.
 */
final class SqliteStagedRollbackDeletion implements StagedRollbackArtifactDeletion {
  private final Path rollbackArtifactPath;
  private boolean finished;

  private SqliteStagedRollbackDeletion(Path rollbackArtifactPath) {
    this.rollbackArtifactPath =
        Objects.requireNonNull(rollbackArtifactPath, "rollbackArtifactPath");
  }

  static SqliteStagedRollbackDeletion create(Path normalizedRollbackArtifactPath) {
    return new SqliteStagedRollbackDeletion(normalizedRollbackArtifactPath);
  }

  @Override
  public void commit() {
    if (finished) {
      return;
    }
    SqliteBookMaintenanceFiles.deleteRollbackArtifact(rollbackArtifactPath);
    finished = true;
  }

  @Override
  public void rollback() {
    if (finished) {
      return;
    }
    finished = true;
  }

  @Override
  public void close() {
    if (!finished) {
      rollback();
    }
  }
}
