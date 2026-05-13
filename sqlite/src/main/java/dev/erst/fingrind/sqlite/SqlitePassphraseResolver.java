package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Public adapter seam that resolves one contract-level passphrase source into SQLite secret bytes.
 */
@FunctionalInterface
public interface SqlitePassphraseResolver {
  /** Resolves the selected passphrase source for the supplied book path and secret intent. */
  ContractDecision<SqliteBookPassphrase> resolve(
      Path bookFilePath,
      BookAccess.PassphraseSource passphraseSource,
      SqlitePassphraseIntent intent);

  /** Resolves the selected passphrase source for the supplied book access tuple. */
  default ContractDecision<SqliteBookPassphrase> resolve(
      BookAccess bookAccess, SqlitePassphraseIntent intent) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return resolve(bookAccess.bookFilePath(), bookAccess.passphraseSource(), intent);
  }
}
