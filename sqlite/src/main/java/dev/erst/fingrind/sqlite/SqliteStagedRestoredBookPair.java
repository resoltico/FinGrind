package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedRestoredBookPair;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Staged restored live-book pair that publishes one re-encrypted book and key file together. */
final class SqliteStagedRestoredBookPair implements StagedRestoredBookPair {
  private final Path stagedBookFilePath;
  private final Path finalBookFilePath;
  private final Path stagedBookKeyFilePath;
  private final Path finalBookKeyFilePath;
  private final @Nullable Path previousBookFilePath;
  private final @Nullable Path previousBookKeyFilePath;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private @Nullable SqliteBookPassphrase restoredPassphrase;
  private boolean finished;

  private SqliteStagedRestoredBookPair(
      Path stagedBookFilePath,
      Path finalBookFilePath,
      Path stagedBookKeyFilePath,
      Path finalBookKeyFilePath,
      @Nullable Path previousBookFilePath,
      @Nullable Path previousBookKeyFilePath,
      SqliteBookPassphrase restoredPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    this.stagedBookFilePath = Objects.requireNonNull(stagedBookFilePath, "stagedBookFilePath");
    this.finalBookFilePath = Objects.requireNonNull(finalBookFilePath, "finalBookFilePath");
    this.stagedBookKeyFilePath =
        Objects.requireNonNull(stagedBookKeyFilePath, "stagedBookKeyFilePath");
    this.finalBookKeyFilePath =
        Objects.requireNonNull(finalBookKeyFilePath, "finalBookKeyFilePath");
    this.previousBookFilePath = previousBookFilePath;
    this.previousBookKeyFilePath = previousBookKeyFilePath;
    this.restoredPassphrase = Objects.requireNonNull(restoredPassphrase, "restoredPassphrase");
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
  }

  static SqliteStagedRestoredBookPair create(
      Path stagedBookFilePath,
      Path finalBookFilePath,
      Path stagedBookKeyFilePath,
      Path finalBookKeyFilePath,
      SqliteBookPassphrase restoredPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    @Nullable Path previousBookFilePath = null;
    @Nullable Path previousBookKeyFilePath = null;
    try {
      if (Files.exists(finalBookFilePath, LinkOption.NOFOLLOW_LINKS)) {
        SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(finalBookFilePath);
        previousBookFilePath =
            SqliteProtectedBookStagingSupport.createStagedSibling(
                finalBookFilePath, ".previous-", ".sqlite");
      }
      if (Files.exists(finalBookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
        SqliteProtectedBookStagingSupport.requireRegularNonSymlinkFile(finalBookKeyFilePath);
        previousBookKeyFilePath =
            SqliteProtectedBookStagingSupport.createStagedSibling(
                finalBookKeyFilePath, ".previous-key-", ".tmp");
      }
      return new SqliteStagedRestoredBookPair(
          stagedBookFilePath,
          finalBookFilePath,
          stagedBookKeyFilePath,
          finalBookKeyFilePath,
          previousBookFilePath,
          previousBookKeyFilePath,
          restoredPassphrase,
          verificationSupport);
    } catch (RuntimeException exception) {
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(previousBookFilePath);
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(previousBookKeyFilePath);
      restoredPassphrase.close();
      throw exception;
    }
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedRestoredBook() {
    return verificationSupport.verifyResolvedBook(
        stagedBookFilePath, currentRestoredPassphrase().copy());
  }

  @Override
  public void commit() {
    if (finished) {
      return;
    }
    try {
      moveTargetIfPresent(finalBookFilePath, previousBookFilePath);
      moveTargetIfPresent(finalBookKeyFilePath, previousBookKeyFilePath);
      SqliteProtectedBookStagingSupport.moveReplacing(stagedBookFilePath, finalBookFilePath);
      SqliteProtectedBookStagingSupport.moveReplacing(stagedBookKeyFilePath, finalBookKeyFilePath);
      SqliteBookFileSecurity.hardenBookArtifacts(finalBookFilePath);
      SqliteBookFileSecurity.hardenOwnerOnlyFile(finalBookKeyFilePath);
      deleteQuietlyIfPresent(previousBookFilePath);
      deleteQuietlyIfPresent(previousBookKeyFilePath);
      closeUnusedPassphrase();
      finished = true;
    } catch (IOException exception) {
      restoreOriginalTarget(finalBookKeyFilePath, previousBookKeyFilePath);
      restoreOriginalTarget(finalBookFilePath, previousBookFilePath);
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(stagedBookFilePath);
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(stagedBookKeyFilePath);
      closeUnusedPassphrase();
      finished = true;
      throw new IllegalStateException(
          "Failed to publish the restored FinGrind live-book pair at "
              + PublicPathHint.fromPath(finalBookFilePath).value()
              + ".",
          exception);
    }
  }

  @Override
  public void rollback() {
    if (finished) {
      return;
    }
    SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(stagedBookFilePath);
    SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(stagedBookKeyFilePath);
    SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(previousBookFilePath);
    SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(previousBookKeyFilePath);
    closeUnusedPassphrase();
    finished = true;
  }

  @Override
  public void close() {
    if (!finished) {
      rollback();
    }
  }

  private SqliteBookPassphrase currentRestoredPassphrase() {
    return Objects.requireNonNull(restoredPassphrase, "restoredPassphrase");
  }

  private void closeUnusedPassphrase() {
    if (restoredPassphrase != null) {
      restoredPassphrase.close();
      restoredPassphrase = null;
    }
  }

  private static void moveTargetIfPresent(Path finalPath, @Nullable Path previousPath)
      throws IOException {
    if (Files.exists(finalPath, LinkOption.NOFOLLOW_LINKS)) {
      SqliteProtectedBookStagingSupport.moveReplacing(
          finalPath, Objects.requireNonNull(previousPath, "previousPath"));
    }
  }

  private static void restoreOriginalTarget(Path finalPath, @Nullable Path previousPath) {
    try {
      if (previousPath != null && Files.exists(previousPath, LinkOption.NOFOLLOW_LINKS)) {
        SqliteProtectedBookStagingSupport.moveReplacing(previousPath, finalPath);
        return;
      }
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(finalPath);
    } catch (IOException restoreFailure) {
      SqliteBestEffort.reportCleanupFailure(
          "restoring one previously published restore target after commit failure", restoreFailure);
    }
  }

  private static void deleteQuietlyIfPresent(@Nullable Path path) {
    SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(path);
  }
}
