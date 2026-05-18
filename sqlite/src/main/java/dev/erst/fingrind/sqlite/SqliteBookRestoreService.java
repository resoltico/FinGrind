package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.RestoreBookResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Restores one verified encrypted backup pair onto one live FinGrind book path. */
public final class SqliteBookRestoreService {
  /** Replaces one closed live book with the selected verified backup pair. */
  public ContractDecision<RestoreBookResult> restoreBook(
      Path bookFilePath,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      SqlitePassphraseResolver passphraseResolver) {
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    Path normalizedBookPath = SqliteBookMaintenanceFiles.normalize(bookFilePath, "bookFilePath");
    Path normalizedBackupFilePath =
        SqliteBookMaintenanceFiles.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupBookKeyFilePath =
        SqliteBookMaintenanceFiles.normalize(backupBookKeyFilePath, "backupBookKeyFilePath");
    List<Path> liveBookBlockingArtifacts =
        SqliteBookMaintenanceFiles.blockingArtifactsForBook(normalizedBookPath);
    if (!liveBookBlockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new RestoreBookResult.Rejected(
              new BookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, liveBookBlockingArtifacts)));
    }
    List<Path> backupBlockingArtifacts =
        SqliteBookMaintenanceFiles.blockingArtifactsForBackupSource(normalizedBackupFilePath);
    if (!backupBlockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new RestoreBookResult.Rejected(
              new BookMaintenanceRejection.BackupSourceHasBlockingArtifacts(
                  normalizedBackupFilePath, backupBlockingArtifacts)));
    }
    BookAccess backupAccess =
        new BookAccess(
            normalizedBackupFilePath,
            new BookAccess.PassphraseSource.KeyFile(normalizedBackupBookKeyFilePath));
    return passphraseResolver
        .resolve(backupAccess, SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            bookPassphrase ->
                restoreWithResolvedPassphrase(
                    normalizedBookPath,
                    normalizedBackupFilePath,
                    normalizedBackupBookKeyFilePath,
                    bookPassphrase),
            ContractDecision::rejected);
  }

  private ContractDecision<RestoreBookResult> restoreWithResolvedPassphrase(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase backupBookPassphrase) {
    try (SqliteBookPassphrase ignored = backupBookPassphrase;
        SqliteBookSession backupSession =
            SqliteBookSessions.open(
                normalizedBackupFilePath, backupBookPassphrase, SqliteBookSessionMode.READ_ONLY)) {
      backupSession.inspectBook();
      SqliteBookMaintenanceFiles.replaceBook(normalizedBackupFilePath, normalizedBookPath);
      return ContractDecision.accepted(
          new RestoreBookResult.Restored(
              normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath));
    }
  }
}
