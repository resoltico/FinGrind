package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns one reusable session secret and mints short-lived working copies for native calls. */
final class SqliteSessionSecret implements AutoCloseable {
  private @Nullable SqliteBookPassphrase durablePassphrase;

  SqliteSessionSecret(SqliteBookPassphrase durablePassphrase) {
    rotateTo(Objects.requireNonNull(durablePassphrase, "durablePassphrase"));
  }

  SqliteOwnedPassphrase borrowWorkingCopy() {
    return new SqliteOwnedPassphrase(copyOf(requireDurablePassphrase()));
  }

  void rotateTo(SqliteBookPassphrase replacementPassphrase) {
    replaceOwnedPassphrase(
        copyOf(Objects.requireNonNull(replacementPassphrase, "replacementPassphrase")));
    replacementPassphrase.close();
  }

  @Override
  public void close() {
    clearDurablePassphrase();
  }

  private SqliteBookPassphrase requireDurablePassphrase() {
    if (durablePassphrase == null) {
      throw new IllegalStateException("SQLite book session secret is no longer available.");
    }
    return durablePassphrase;
  }

  private void replaceOwnedPassphrase(SqliteBookPassphrase replacementPassphrase) {
    clearDurablePassphrase();
    durablePassphrase = replacementPassphrase;
  }

  private void clearDurablePassphrase() {
    if (durablePassphrase != null) {
      durablePassphrase.close();
      durablePassphrase = null;
    }
  }

  private static SqliteBookPassphrase copyOf(SqliteBookPassphrase sourcePassphrase) {
    return sourcePassphrase.copy();
  }
}
