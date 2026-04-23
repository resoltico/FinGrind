package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import java.nio.file.Path;
import java.util.Objects;

/** Canonical same-package rules for SQLite file-backed book access. */
final class SqliteBookAccessRules {
  private SqliteBookAccessRules() {}

  static Path requireKeyFile(BookAccess bookAccess) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    return switch (bookAccess.passphraseSource()) {
      case BookAccess.PassphraseSource.KeyFile keyFile -> keyFile.bookKeyFilePath();
      case BookAccess.PassphraseSource.StandardInput source ->
          throw unsupportedPassphraseSource(source);
      case BookAccess.PassphraseSource.InteractivePrompt source ->
          throw unsupportedPassphraseSource(source);
    };
  }

  private static IllegalArgumentException unsupportedPassphraseSource(
      BookAccess.PassphraseSource source) {
    return new IllegalArgumentException(
        "SQLite same-package file-backed stores require a "
            + ProtocolOptions.BOOK_KEY_FILE
            + " access selection, not "
            + source.optionName()
            + ".");
  }
}
