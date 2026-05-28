package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.maintenance.MaintenanceDecision;
import dev.erst.fingrind.executor.spi.ProtectedBookMaintenanceStore;
import dev.erst.fingrind.executor.spi.StagedBackupPair;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Staged encrypted backup pair implementation. */
final class SqliteStagedBackupPair implements StagedBackupPair {
  private final Path stagedBackupFilePath;
  private final Path finalBackupFilePath;
  private final Path finalBackupBookKeyFilePath;
  private final SqliteProtectedBookVerificationSupport verificationSupport;
  private @Nullable SqliteBookPassphrase backupPassphrase;
  private boolean backupFilePublished;
  private boolean backupKeyFilePublished;
  private boolean finished;

  SqliteStagedBackupPair(
      Path stagedBackupFilePath,
      Path finalBackupFilePath,
      Path finalBackupBookKeyFilePath,
      SqliteBookPassphrase backupPassphrase,
      SqliteProtectedBookVerificationSupport verificationSupport) {
    this.stagedBackupFilePath =
        Objects.requireNonNull(stagedBackupFilePath, "stagedBackupFilePath");
    this.finalBackupFilePath = Objects.requireNonNull(finalBackupFilePath, "finalBackupFilePath");
    this.finalBackupBookKeyFilePath =
        Objects.requireNonNull(finalBackupBookKeyFilePath, "finalBackupBookKeyFilePath");
    this.backupPassphrase = Objects.requireNonNull(backupPassphrase, "backupPassphrase");
    this.verificationSupport = Objects.requireNonNull(verificationSupport, "verificationSupport");
  }

  @Override
  public MaintenanceDecision<ProtectedBookMaintenanceStore.BookVerification>
      verifyInitializedBackup() {
    return verificationSupport.verifyResolvedBook(
        stagedBackupFilePath, currentBackupPassphrase().copy());
  }

  @Override
  public void commit() {
    if (finished) {
      return;
    }
    @Nullable Path stagedBackupBookKeyFilePath = null;
    try (SqliteBookPassphrase committedBackupPassphrase = takeBackupPassphrase()) {
      stagedBackupBookKeyFilePath =
          SqliteProtectedBookStagingSupport.createStagedSibling(
              finalBackupBookKeyFilePath, ".backup-key-", ".tmp");
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(stagedBackupBookKeyFilePath);
      try (SqliteBookPassphrase materializedPassphrase = committedBackupPassphrase.copy()) {
        SqliteBookKeyFile.materialize(stagedBackupBookKeyFilePath, materializedPassphrase);
      }
      SqliteProtectedBookStagingSupport.moveReplacing(stagedBackupFilePath, finalBackupFilePath);
      backupFilePublished = true;
      SqliteProtectedBookStagingSupport.moveReplacing(
          stagedBackupBookKeyFilePath, finalBackupBookKeyFilePath);
      backupKeyFilePublished = true;
      SqliteBookFileSecurity.hardenBookArtifacts(finalBackupFilePath);
      SqliteBookFileSecurity.hardenOwnerOnlyFile(finalBackupBookKeyFilePath);
      finished = true;
    } catch (IOException exception) {
      SqliteProtectedBookStagingSupport.deleteQuietlyIfPresent(stagedBackupBookKeyFilePath);
      rollbackPublishedBackupArtifacts();
      finished = true;
      throw new IllegalStateException(
          "Failed to publish the staged FinGrind backup pair.", exception);
    }
  }

  @Override
  public void rollback() {
    if (finished) {
      return;
    }
    rollbackPublishedBackupArtifacts();
    closeUnusedBackupPassphrase();
    finished = true;
  }

  @Override
  public void close() {
    if (!finished) {
      rollback();
    }
  }

  private SqliteBookPassphrase currentBackupPassphrase() {
    return Objects.requireNonNull(backupPassphrase, "backupPassphrase");
  }

  private SqliteBookPassphrase takeBackupPassphrase() {
    SqliteBookPassphrase passphrase = currentBackupPassphrase();
    backupPassphrase = null;
    return passphrase;
  }

  private void closeUnusedBackupPassphrase() {
    if (backupPassphrase != null) {
      backupPassphrase.close();
      backupPassphrase = null;
    }
  }

  private void rollbackPublishedBackupArtifacts() {
    if (backupKeyFilePublished) {
      SqliteBookKeyFileGenerator.deleteQuietly(finalBackupBookKeyFilePath);
    }
    if (backupFilePublished) {
      SqliteBookKeyFileGenerator.deleteQuietly(finalBackupFilePath);
    }
    SqliteBookKeyFileGenerator.deleteQuietly(stagedBackupFilePath);
  }
}
