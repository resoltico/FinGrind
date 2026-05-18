package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BackupBookResult;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Exports one closed encrypted-book backup pair from an initialized FinGrind book. */
public final class SqliteBookBackupService {
  /** Creates one backup copy of the selected closed book and writes the paired key file. */
  public ContractDecision<BackupBookResult> backupBook(
      BookAccess bookAccess,
      Path backupFilePath,
      Path backupBookKeyFilePath,
      SqlitePassphraseResolver passphraseResolver) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(passphraseResolver, "passphraseResolver");
    Path normalizedBookPath =
        SqliteBookMaintenanceFiles.normalize(bookAccess.bookFilePath(), "bookFilePath");
    Path normalizedBackupFilePath =
        SqliteBookMaintenanceFiles.normalize(backupFilePath, "backupFilePath");
    Path normalizedBackupBookKeyFilePath =
        SqliteBookMaintenanceFiles.normalize(backupBookKeyFilePath, "backupBookKeyFilePath");
    List<Path> blockingArtifacts =
        SqliteBookMaintenanceFiles.blockingArtifactsForBook(normalizedBookPath);
    if (!blockingArtifacts.isEmpty()) {
      return ContractDecision.accepted(
          new BackupBookResult.Rejected(
              new BookMaintenanceRejection.BookHasBlockingArtifacts(
                  normalizedBookPath, blockingArtifacts)));
    }
    if (Files.exists(normalizedBackupFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return ContractDecision.accepted(
          new BackupBookResult.Rejected(
              new BookMaintenanceRejection.BackupDestinationAlreadyExists(
                  normalizedBackupFilePath)));
    }
    if (Files.exists(normalizedBackupBookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
      return ContractDecision.accepted(
          new BackupBookResult.Rejected(
              new BookMaintenanceRejection.BackupKeyFileAlreadyExists(
                  normalizedBackupBookKeyFilePath)));
    }
    return passphraseResolver
        .resolve(bookAccess, SqlitePassphraseIntent.EXISTING_SECRET)
        .fold(
            bookPassphrase ->
                backupWithResolvedPassphrase(
                    normalizedBookPath,
                    normalizedBackupFilePath,
                    normalizedBackupBookKeyFilePath,
                    bookPassphrase),
            ContractDecision::rejected);
  }

  private ContractDecision<BackupBookResult> backupWithResolvedPassphrase(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath,
      SqliteBookPassphrase bookPassphrase) {
    try (BackupPassphrases passphrases = BackupPassphrases.copyOf(bookPassphrase)) {
      verifyClosedReadableBook(normalizedBookPath, passphrases.bookPassphrase());
      SqliteBookKeyFileMaterializer.materialize(
          normalizedBackupBookKeyFilePath, passphrases.backupKeyPassphrase());
      return copyBackupPair(
          normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath);
    }
  }

  private static void verifyClosedReadableBook(
      Path normalizedBookPath, SqliteBookPassphrase bookPassphrase) {
    try (SqliteBookSession bookSession =
        SqliteBookSessions.open(
            normalizedBookPath, bookPassphrase, SqliteBookSessionMode.READ_ONLY)) {
      bookSession.inspectBook();
    }
  }

  private static ContractDecision<BackupBookResult> copyBackupPair(
      Path normalizedBookPath,
      Path normalizedBackupFilePath,
      Path normalizedBackupBookKeyFilePath) {
    try {
      SqliteBookMaintenanceFiles.copyFreshBook(normalizedBookPath, normalizedBackupFilePath);
      return ContractDecision.accepted(
          new BackupBookResult.BackedUp(
              normalizedBookPath, normalizedBackupFilePath, normalizedBackupBookKeyFilePath));
    } catch (RuntimeException exception) {
      SqliteBookKeyFileGenerator.deleteQuietly(normalizedBackupBookKeyFilePath);
      throw exception;
    }
  }

  /** Owns the source and copied passphrases for one backup publication attempt. */
  private static final class BackupPassphrases implements AutoCloseable {
    private final SqliteBookPassphrase bookPassphrase;
    private final SqliteBookPassphrase backupKeyPassphrase;

    private BackupPassphrases(
        SqliteBookPassphrase bookPassphrase, SqliteBookPassphrase backupKeyPassphrase) {
      this.bookPassphrase = bookPassphrase;
      this.backupKeyPassphrase = backupKeyPassphrase;
    }

    private static BackupPassphrases copyOf(SqliteBookPassphrase bookPassphrase) {
      return new BackupPassphrases(bookPassphrase, bookPassphrase.copy());
    }

    private SqliteBookPassphrase bookPassphrase() {
      return bookPassphrase;
    }

    private SqliteBookPassphrase backupKeyPassphrase() {
      return backupKeyPassphrase;
    }

    @Override
    public void close() {
      backupKeyPassphrase.close();
      bookPassphrase.close();
    }
  }
}
