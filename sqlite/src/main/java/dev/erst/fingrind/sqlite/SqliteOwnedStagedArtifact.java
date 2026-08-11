package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns one recorded maintenance stage and releases local authority without unlinking it. */
final class SqliteOwnedStagedArtifact {
  private final SqliteOwnedStageRecord record;
  private boolean released;

  private SqliteOwnedStagedArtifact(SqliteOwnedStageRecord record) {
    this.record = Objects.requireNonNull(record, "record");
  }

  static SqliteOwnedStagedArtifact create(Path finalPath, String infix, String suffix) {
    return new SqliteOwnedStagedArtifact(SqliteOwnedStageRecord.create(finalPath, infix, suffix));
  }

  /** Records an already-created stage so fixture seams exercise retained-stage ownership. */
  static SqliteOwnedStagedArtifact recordExisting(Path finalPath, Path stagedPath) {
    return new SqliteOwnedStagedArtifact(
        SqliteOwnedStageRecord.recordExisting(finalPath, stagedPath));
  }

  Path stagedPath() {
    return record.stagedPath();
  }

  /** Requires the backing record to still prove this artifact is safe to publish. */
  void requireIntactFor(Path finalPath) {
    if (released) {
      throw new IllegalStateException("The FinGrind maintenance stage was already released.");
    }
    record.requireIntactFor(finalPath);
  }

  void releaseRetained() {
    if (released) {
      return;
    }
    record.releaseRetained();
    released = true;
  }

  static void releaseAllRetained(
      @Nullable SqliteOwnedStagedArtifact first, @Nullable SqliteOwnedStagedArtifact second) {
    if (first == null) {
      releaseRetained(second);
      return;
    }
    try {
      first.releaseRetained();
    } finally {
      releaseRetained(second);
    }
  }

  private static void releaseRetained(@Nullable SqliteOwnedStagedArtifact artifact) {
    if (artifact != null) {
      artifact.releaseRetained();
    }
  }
}
