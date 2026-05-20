package dev.erst.fingrind.sqlite;

import static java.nio.charset.StandardCharsets.UTF_8;

/** SQLite-owned passphrase inspection helpers for tests outside the FFM bridge. */
public final class SqliteBookPassphraseTestSupport {
  private SqliteBookPassphraseTestSupport() {}

  /** Returns the current UTF-8 passphrase bytes decoded as one Java string. */
  public static String utf8String(SqliteBookPassphrase passphrase) {
    return new String(passphrase.utf8BytesCopy(), UTF_8);
  }
}
