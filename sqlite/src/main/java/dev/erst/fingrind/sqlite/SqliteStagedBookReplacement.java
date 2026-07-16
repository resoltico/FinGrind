package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.StagedBookReplacement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Reversible staged replacement that delays the live swap until commit. */
final class SqliteStagedBookReplacement implements StagedBookReplacement {
  private final SqliteOwnedStagedArtifact stagedBook;
  private final Path targetBookPath;
  private final @Nullable SqliteOwnedStagedArtifact previousTargetBackup;
  private boolean finished;

  SqliteStagedBookReplacement(
      SqliteOwnedStagedArtifact stagedBook,
      Path targetBookPath,
      @Nullable SqliteOwnedStagedArtifact previousTargetBackup) {
    this.stagedBook = Objects.requireNonNull(stagedBook, "stagedBook");
    this.targetBookPath = Objects.requireNonNull(targetBookPath, "targetBookPath");
    this.previousTargetBackup = previousTargetBackup;
  }

  static SqliteStagedBookReplacement create(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    @Nullable SqliteOwnedStagedArtifact stagedReplacement = null;
    @Nullable SqliteOwnedStagedArtifact previousTargetBackup = null;
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedTargetBookPath);
      SqliteOwnedStagedArtifact.recoverFor(normalizedTargetBookPath);
      stagedReplacement =
          SqliteOwnedStagedArtifact.create(normalizedTargetBookPath, ".restore-", ".tmp");
      Files.copy(
          normalizedSourceBookPath,
          stagedReplacement.stagedPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      SqliteBookFileSecurity.hardenBookArtifacts(stagedReplacement.stagedPath());
      if (Files.exists(normalizedTargetBookPath, LinkOption.NOFOLLOW_LINKS)) {
        SqliteProtectedBookStagingFiles.requireRegularNonSymlinkFile(normalizedTargetBookPath);
        previousTargetBackup =
            SqliteOwnedStagedArtifact.create(normalizedTargetBookPath, ".previous-", ".sqlite");
      }
      return new SqliteStagedBookReplacement(
          stagedReplacement, normalizedTargetBookPath, previousTargetBackup);
    } catch (IOException exception) {
      SqliteOwnedStagedArtifact.discardAll(stagedReplacement, previousTargetBackup);
      throw new IllegalStateException(
          "Failed to stage the FinGrind SQLite book replacement at "
              + SqliteMachinePaths.absoluteValue(normalizedTargetBookPath)
              + ".",
          exception);
    }
  }

  @Override
  public Path stagedBookPath() {
    return stagedBook.stagedPath();
  }

  @Override
  public void commit() {
    if (finished) {
      return;
    }
    try {
      if (Files.exists(targetBookPath, LinkOption.NOFOLLOW_LINKS)) {
        SqliteProtectedBookPublicationSupport.moveReplacing(
            targetBookPath,
            Objects.requireNonNull(previousTargetBackup, "previousTargetBackup").stagedPath());
      }
      SqliteProtectedBookPublicationSupport.moveReplacing(stagedBook.stagedPath(), targetBookPath);
      discardStagedArtifacts();
      finished = true;
    } catch (IOException exception) {
      if (!Files.exists(targetBookPath, LinkOption.NOFOLLOW_LINKS)
          && previousTargetBackup != null
          && Files.exists(previousTargetBackup.stagedPath(), LinkOption.NOFOLLOW_LINKS)) {
        try {
          SqliteProtectedBookPublicationSupport.moveReplacing(
              previousTargetBackup.stagedPath(), targetBookPath);
        } catch (IOException restoreException) {
          SqliteBestEffort.reportCleanupFailure(
              "restoring one previous protected book after replacement commit failure",
              restoreException);
        }
      }
      throw new IllegalStateException(
          "Failed to commit the staged FinGrind SQLite replacement at "
              + SqliteMachinePaths.absoluteValue(targetBookPath)
              + ".",
          exception);
    }
  }

  @Override
  public void rollback() {
    if (finished) {
      return;
    }
    discardStagedArtifacts();
    finished = true;
  }

  @Override
  public void close() {
    if (!finished) {
      rollback();
    }
  }

  private void discardStagedArtifacts() {
    stagedBook.discard();
    if (previousTargetBackup != null) {
      previousTargetBackup.discard();
    }
  }
}
