package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.Objects;

/** Canonical same-package rules for SQLite file-backed book access. */
final class SqliteBookAccessRules {
  private SqliteBookAccessRules() {}

  static ContractDecision<Path> requireKeyFile(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile ->
          ContractDecision.accepted(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput source ->
          ContractDecision.rejected(unsupportedPassphraseSource(source));
      case BookAccess.PassphraseSource.InteractivePrompt source ->
          ContractDecision.rejected(unsupportedPassphraseSource(source));
    };
  }

  private static dev.erst.fingrind.contract.ContractFailure unsupportedPassphraseSource(
      BookAccess.PassphraseSource source) {
    return ContractErrors.Descriptor.INVALID_BOOK_PASSPHRASE_SOURCE.failure(
        "SQLite same-package file-backed stores require a "
            + ProtocolOptions.BOOK_KEY_FILE
            + " access selection, not "
            + source.optionName()
            + ".",
        null,
        null);
  }
}
