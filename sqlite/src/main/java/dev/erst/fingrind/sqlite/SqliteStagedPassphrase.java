package dev.erst.fingrind.sqlite;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Owns the passphrase retained by a staged maintenance operation until it is consumed or closed.
 */
final class SqliteStagedPassphrase {
  private @Nullable SqliteBookPassphrase passphrase;

  SqliteStagedPassphrase(String purpose, byte[] utf8Bytes) {
    passphrase =
        SqliteBookPassphrase.fromUtf8Bytes(
            Objects.requireNonNull(purpose, "purpose"),
            Objects.requireNonNull(utf8Bytes, "utf8Bytes"));
  }

  SqliteBookPassphrase copy() {
    return current().copy();
  }

  SqliteBookPassphrase take() {
    SqliteBookPassphrase taken = current();
    passphrase = null;
    return taken;
  }

  void closeUnused() {
    if (passphrase != null) {
      passphrase.close();
      passphrase = null;
    }
  }

  private SqliteBookPassphrase current() {
    return Objects.requireNonNull(passphrase, "staged passphrase");
  }
}
