package dev.erst.fingrind.sqlite;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

/** Best-effort heap/direct buffer zeroization helpers for temporary passphrase material. */
final class SqliteBookPassphraseZeroization {
  private SqliteBookPassphraseZeroization() {}

  static void zeroize(ByteBuffer encodedBytes) {
    ByteBuffer duplicate = encodedBytes.duplicate();
    if (duplicate.hasArray()) {
      int startIndex = duplicate.arrayOffset();
      int endIndex = startIndex + duplicate.limit();
      Arrays.fill(duplicate.array(), startIndex, endIndex, (byte) 0);
      return;
    }
    for (int index = 0; index < duplicate.limit(); index++) {
      duplicate.put(index, (byte) 0);
    }
  }

  static void zeroize(CharBuffer decodedCharacters) {
    CharBuffer duplicate = decodedCharacters.duplicate();
    if (duplicate.hasArray()) {
      int startIndex = duplicate.arrayOffset();
      int endIndex = startIndex + duplicate.limit();
      Arrays.fill(duplicate.array(), startIndex, endIndex, '\0');
      return;
    }
    for (int index = 0; index < duplicate.limit(); index++) {
      duplicate.put(index, '\0');
    }
  }
}
