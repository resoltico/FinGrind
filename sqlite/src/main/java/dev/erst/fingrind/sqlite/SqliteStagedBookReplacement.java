package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
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
  private final Path stagedBookPath;
  private final Path targetBookPath;
  private final @Nullable Path previousTargetBackupPath;
  private boolean finished;

  SqliteStagedBookReplacement(
      Path stagedBookPath, Path targetBookPath, @Nullable Path previousTargetBackupPath) {
    this.stagedBookPath = Objects.requireNonNull(stagedBookPath, "stagedBookPath");
    this.targetBookPath = Objects.requireNonNull(targetBookPath, "targetBookPath");
    this.previousTargetBackupPath = previousTargetBackupPath;
  }

  static SqliteStagedBookReplacement create(
      Path normalizedSourceBookPath, Path normalizedTargetBookPath) {
    @Nullable Path stagedReplacementPath = null;
    @Nullable Path previousTargetBackupPath = null;
    try {
      SqliteBookFileSecurity.ensureSecureParentDirectory(normalizedTargetBookPath);
      SqliteBookMaintenanceFiles.cleanupAbandonedStageArtifacts(normalizedTargetBookPath);
      Path targetParentDirectory =
          Objects.requireNonNull(
              normalizedTargetBookPath.getParent(), "normalizedTargetBookPath parent");
      String targetFileName =
          Objects.requireNonNull(
                  normalizedTargetBookPath.getFileName(), "normalizedTargetBookPath fileName")
              .toString();
      stagedReplacementPath =
          Files.createTempFile(targetParentDirectory, targetFileName + ".restore-", ".tmp");
      Files.copy(
          normalizedSourceBookPath,
          stagedReplacementPath,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.COPY_ATTRIBUTES);
      SqliteBookFileSecurity.hardenBookArtifacts(stagedReplacementPath);
      if (Files.exists(normalizedTargetBookPath, LinkOption.NOFOLLOW_LINKS)) {
        SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(normalizedTargetBookPath);
        previousTargetBackupPath =
            Files.createTempFile(targetParentDirectory, targetFileName + ".previous-", ".sqlite");
      }
      return new SqliteStagedBookReplacement(
          stagedReplacementPath, normalizedTargetBookPath, previousTargetBackupPath);
    } catch (IOException exception) {
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(stagedReplacementPath);
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(previousTargetBackupPath);
      throw new IllegalStateException(
          "Failed to stage the FinGrind SQLite book replacement at "
              + PublicPathHint.fromPath(normalizedTargetBookPath).value()
              + ".",
          exception);
    }
  }

  @Override
  public Path stagedBookPath() {
    return stagedBookPath;
  }

  @Override
  public void commit() {
    if (finished) {
      return;
    }
    try {
      if (Files.exists(targetBookPath, LinkOption.NOFOLLOW_LINKS)) {
        SqliteProtectedBookStagingSupport.moveReplacing(
            targetBookPath,
            Objects.requireNonNull(previousTargetBackupPath, "previousTargetBackupPath"));
      }
      SqliteProtectedBookStagingSupport.moveReplacing(stagedBookPath, targetBookPath);
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(previousTargetBackupPath);
      finished = true;
    } catch (IOException exception) {
      if (!Files.exists(targetBookPath, LinkOption.NOFOLLOW_LINKS)
          && previousTargetBackupPath != null
          && Files.exists(previousTargetBackupPath, LinkOption.NOFOLLOW_LINKS)) {
        try {
          SqliteProtectedBookStagingSupport.moveReplacing(previousTargetBackupPath, targetBookPath);
        } catch (IOException restoreException) {
          SqliteBestEffort.reportCleanupFailure(
              "restoring one previous protected book after replacement commit failure",
              restoreException);
        }
      }
      throw new IllegalStateException(
          "Failed to commit the staged FinGrind SQLite replacement at "
              + PublicPathHint.fromPath(targetBookPath).value()
              + ".",
          exception);
    }
  }

  @Override
  public void rollback() {
    if (finished) {
      return;
    }
    SqliteBookKeyFileGenerator.deleteQuietly(stagedBookPath);
    SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(previousTargetBackupPath);
    finished = true;
  }

  @Override
  public void close() {
    if (!finished) {
      rollback();
    }
  }
}
