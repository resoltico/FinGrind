package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** One explicitly owned passphrase that callers must close through the owning workflow. */
final class SqliteOwnedPassphrase {
  private final SqliteBookPassphrase nativePassphrase;

  SqliteOwnedPassphrase(SqliteBookPassphrase nativePassphrase) {
    this.nativePassphrase = Objects.requireNonNull(nativePassphrase, "nativePassphrase");
  }

  SqliteBookPassphrase nativePassphrase() {
    return nativePassphrase;
  }

  void close() {
    nativePassphrase.close();
  }
}
