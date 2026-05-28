package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Unit tests for native secret buffer ownership and zeroization. */
class SqliteNativeSecretBufferTest {
  @Test
  void cString_copiesPassphraseBytesAndZeroizesTheNativeBufferOnClose() {
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters("native-secret-buffer", "abc123".toCharArray());
        Arena arena = Arena.ofConfined()) {
      byte[] expectedBytes = "abc123".getBytes(StandardCharsets.UTF_8);
      MemorySegment pointer;
      try (SqliteNativeSecretBuffer buffer = SqliteNativeSecretBuffer.cString(passphrase, arena)) {
        pointer = buffer.pointer();
        assertArrayEquals(
            new byte[] {'a', 'b', 'c', '1', '2', '3', 0}, pointer.toArray(ValueLayout.JAVA_BYTE));
      }

      assertArrayEquals(new byte[expectedBytes.length + 1], pointer.toArray(ValueLayout.JAVA_BYTE));
    }
  }

  @Test
  void close_isIdempotentAfterZeroization() {
    try (SqliteBookPassphrase passphrase =
            SqliteBookPassphrase.fromCharacters(
                "native-secret-buffer-idempotent", "abc123".toCharArray());
        Arena arena = Arena.ofConfined()) {
      byte[] expectedBytes = "abc123".getBytes(StandardCharsets.UTF_8);
      MemorySegment pointer;
      try (SqliteNativeSecretBuffer buffer = SqliteNativeSecretBuffer.cString(passphrase, arena)) {
        pointer = buffer.pointer();
        buffer.close();
      }

      assertArrayEquals(new byte[expectedBytes.length + 1], pointer.toArray(ValueLayout.JAVA_BYTE));
    }
  }
}
