package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns one recorded maintenance stage and its recursive cleanup boundary. */
final class SqliteOwnedStagedArtifact {
  private final SqliteOwnedStageRecord record;
  private boolean released;

  private SqliteOwnedStagedArtifact(SqliteOwnedStageRecord record) {
    this.record = Objects.requireNonNull(record, "record");
  }

  static SqliteOwnedStagedArtifact create(Path finalPath, String infix, String suffix) {
    return new SqliteOwnedStagedArtifact(SqliteOwnedStageRecord.create(finalPath, infix, suffix));
  }

  /** Records an already-created stage so fixture seams exercise the production cleanup contract. */
  static SqliteOwnedStagedArtifact recordExisting(Path finalPath, Path stagedPath) {
    return new SqliteOwnedStagedArtifact(
        SqliteOwnedStageRecord.recordExisting(finalPath, stagedPath));
  }

  /** Recovers exact durable stages for one target without treating a name pattern as ownership. */
  static void recoverFor(Path finalPath) {
    SqliteOwnedStageRecord.findFor(finalPath)
        .forEach(record -> new SqliteOwnedStagedArtifact(record).discard());
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

  /** Reserves ownership while allowing a native API to create the staged artifact itself. */
  void vacateForNativeMaterialization(Path finalPath) {
    if (released) {
      throw new IllegalStateException("The FinGrind maintenance stage was already released.");
    }
    record.vacateForNativeMaterialization(finalPath);
  }

  void discard() {
    if (released) {
      return;
    }
    recoverFor(stagedPath());
    record.discard();
    released = true;
  }

  static void discardAll(
      @Nullable SqliteOwnedStagedArtifact first, @Nullable SqliteOwnedStagedArtifact second) {
    if (first == null) {
      discard(second);
      return;
    }
    try {
      first.discard();
    } finally {
      discard(second);
    }
  }

  private static void discard(@Nullable SqliteOwnedStagedArtifact artifact) {
    if (artifact != null) {
      artifact.discard();
    }
  }
}
