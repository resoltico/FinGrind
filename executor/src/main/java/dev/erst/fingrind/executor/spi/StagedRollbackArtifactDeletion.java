package dev.erst.fingrind.executor.spi;

/** Reversible staged deletion for one rollback artifact. */
public interface StagedRollbackArtifactDeletion extends AutoCloseable {
  /** Commits the staged rollback-artifact deletion. */
  void commit();

  /** Restores the rollback artifact under its original path. */
  void rollback();

  @Override
  void close();
}
