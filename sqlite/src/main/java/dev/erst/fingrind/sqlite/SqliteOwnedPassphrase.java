package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.util.Objects;

/** One explicitly owned passphrase that callers must close through the owning workflow. */
final class SqliteOwnedPassphrase implements AutoCloseable {
  private final SqliteBookPassphrase nativePassphrase;

  SqliteOwnedPassphrase(SqliteBookPassphrase nativePassphrase) {
    this.nativePassphrase = Objects.requireNonNull(nativePassphrase, "nativePassphrase");
  }

  SqliteBookPassphrase nativePassphrase() {
    return nativePassphrase;
  }

  @Override
  public void close() {
    nativePassphrase.close();
  }
}
