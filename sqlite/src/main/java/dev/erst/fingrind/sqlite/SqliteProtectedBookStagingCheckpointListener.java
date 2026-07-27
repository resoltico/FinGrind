package dev.erst.fingrind.sqlite;

/** Observes one protected-book staging boundary before it mutates staged artifacts. */
@FunctionalInterface
interface SqliteProtectedBookStagingCheckpointListener {
  /** Observes one named staging boundary. */
  void reached(SqliteProtectedBookStagingCheckpoint checkpoint);

  /** Returns the production listener that intentionally observes no test checkpoints. */
  static SqliteProtectedBookStagingCheckpointListener none() {
    return checkpoint -> {};
  }
}
