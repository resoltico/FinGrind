package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Owns one source passphrase for a staging attempt and wipes it on every exit path. */
final class SqliteStagingSourcePassphraseLease {
  private final SqliteBookPassphrase passphrase;

  private SqliteStagingSourcePassphraseLease(SqliteBookPassphrase passphrase) {
    this.passphrase = Objects.requireNonNull(passphrase, "sourcePassphrase");
  }

  static SqliteStagingSourcePassphraseLease take(SqliteBookPassphrase passphrase) {
    return new SqliteStagingSourcePassphraseLease(passphrase);
  }

  SqliteBookPassphrase passphrase() {
    return passphrase;
  }

  void wipe() {
    passphrase.close();
  }
}
