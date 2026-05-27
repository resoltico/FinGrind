package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.sqlite.secret.SqliteBookKeyFile;
import java.nio.file.Path;
import java.util.Objects;

/** Opens SQLite databases by first resolving passphrase material from a key file. */
final class SqliteNativeKeyFileAccess {
  private SqliteNativeKeyFileAccess() {}

  static SqliteNativeDatabase open(Path bookPath, Path keyFilePath) {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(keyFilePath, "keyFilePath");
    return SqliteBookKeyFile.loadDecision(keyFilePath)
        .fold(
            bookPassphrase -> {
              try (bookPassphrase) {
                return SqliteNativeConnections.open(
                    bookPath, bookPassphrase, SqliteNativeOpenMode.READ_WRITE_CREATE);
              }
            },
            failure -> {
              throw new ContractFailureException(failure);
            });
  }
}
