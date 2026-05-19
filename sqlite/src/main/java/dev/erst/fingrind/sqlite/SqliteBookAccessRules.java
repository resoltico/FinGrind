package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.nio.file.Path;
import java.util.Objects;

/** Canonical same-package rules for SQLite file-backed book access. */
final class SqliteBookAccessRules {
  private SqliteBookAccessRules() {}

  static ContractDecision<Path> requireKeyFile(BookAccess.PassphraseSource passphraseSource) {
    Objects.requireNonNull(passphraseSource, "passphraseSource");
    return switch (passphraseSource) {
      case BookAccess.PassphraseSource.KeyFile keyFile ->
          ContractDecision.accepted(keyFile.bookKeyFilePath());
      case BookAccess.PassphraseSource.StandardInput source ->
          ContractDecision.rejected(unsupportedPassphraseSource(source));
      case BookAccess.PassphraseSource.InteractivePrompt source ->
          ContractDecision.rejected(unsupportedPassphraseSource(source));
    };
  }

  private static dev.erst.fingrind.contract.runtime.ContractFailure unsupportedPassphraseSource(
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
