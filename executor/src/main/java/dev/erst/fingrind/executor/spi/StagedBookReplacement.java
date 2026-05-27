package dev.erst.fingrind.executor.spi;

import java.nio.file.Path;

/** Reversible staged replacement prepared for one restore-style workflow. */
public interface StagedBookReplacement extends AutoCloseable {
  /** Staged replacement path that can be verified before the live target is replaced. */
  Path stagedBookPath();

  /** Commits the staged replacement and discards the previous-target rollback copy. */
  void commit();

  /** Discards the staged replacement and restores any prior target snapshot if one exists. */
  void rollback();

  @Override
  void close();
}
