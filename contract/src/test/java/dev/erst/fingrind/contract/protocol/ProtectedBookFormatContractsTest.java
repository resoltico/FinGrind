package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Coverage and defensive-path tests for the protected-book format contract loader. */
class ProtectedBookFormatContractsTest {
  @Test
  void loadFromResource_rejectsMissingStream() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> ProtectedBookFormatContracts.loadFromResource(null, "/missing.json"));

    assertEquals(
        "Missing protected-book format contract resource: /missing.json", exception.getMessage());
  }

  @Test
  void loadFromResource_wrapsIoFailures() {
    UncheckedIOException exception =
        assertThrows(
            UncheckedIOException.class,
            () ->
                ProtectedBookFormatContracts.loadFromResource(
                    failingInputStream(), "/broken.json"));

    assertEquals(
        "Failed to load protected-book format contract resource: /broken.json",
        exception.getMessage());
    assertEquals("boom", Objects.requireNonNull(exception.getCause()).getMessage());
  }

  @Test
  void loadFromResource_rejectsWrongValueKinds() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProtectedBookFormatContracts.loadFromResource(
                    new ByteArrayInputStream(
                        """
                        {
                          "applicationId": 1179079236,
                          "formatVersion": 12,
                          "cipher": "chacha20",
                          "legacyMode": "false",
                          "pageSize": 4096,
                          "reservedBytes": 32,
                          "legacyPageSize": 4096,
                          "kdfIter": 64007,
                          "plaintextHeaderSize": 0
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)),
                    "/invalid.json"));

    assertEquals("legacyMode must be one JSON boolean.", exception.getMessage());
  }

  @Test
  void loadFromResource_returnsTypedContract() {
    ProtectedBookFormatContract contract =
        ProtectedBookFormatContracts.loadFromResource(
            new ByteArrayInputStream(
                """
                {
                  "applicationId": 1179079236,
                  "formatVersion": 12,
                  "cipher": "chacha20",
                  "legacyMode": false,
                  "pageSize": 4096,
                  "reservedBytes": 32,
                  "legacyPageSize": 4096,
                  "kdfIter": 64007,
                  "plaintextHeaderSize": 0
                }
                """
                    .getBytes(StandardCharsets.UTF_8)),
            "/protected-book-format-contract.json");

    assertEquals(1_179_079_236, contract.applicationId());
    assertEquals(12, contract.formatVersion());
    assertEquals(BookCipher.CHACHA20, contract.cipher());
    assertFalse(contract.legacyMode());
    assertEquals(4096, contract.pageSize());
    assertEquals(32, contract.reservedBytes());
    assertEquals(4096, contract.legacyPageSize());
    assertEquals(64007, contract.kdfIter());
    assertEquals(0, contract.plaintextHeaderSize());
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }
}
