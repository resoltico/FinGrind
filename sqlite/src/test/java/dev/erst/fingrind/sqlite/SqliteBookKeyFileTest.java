package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the SQLite book-key file loader. */
class SqliteBookKeyFileTest {
  @TempDir Path tempDirectory;

  @Test
  void load_acceptsUtf8PassphraseAndStripsOneTrailingLineEnding() throws Exception {
    Path keyFile = tempDirectory.resolve("book.key");
    Files.writeString(keyFile, "swordfish\n", StandardCharsets.UTF_8);

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile);
        Arena arena = Arena.ofConfined()) {
      assertEquals(
          keyFile.toAbsolutePath().normalize().toString(), keyMaterial.sourceDescription());
      assertEquals(9, keyMaterial.byteLength());
      assertEquals(
          "swordfish",
          new String(
              keyMaterial
                  .copyToCString(arena)
                  .asSlice(0, keyMaterial.byteLength())
                  .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE),
              StandardCharsets.UTF_8));
    }
  }

  @Test
  void load_acceptsUtf8PassphraseAndStripsOneTrailingCrLf() throws Exception {
    Path keyFile = tempDirectory.resolve("book-crlf.key");
    Files.writeString(keyFile, "swordfish\r\n", StandardCharsets.UTF_8);

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(9, keyMaterial.byteLength());
    }
  }

  @Test
  void load_acceptsUtf8PassphraseWithoutTrailingLineEnding() throws Exception {
    Path keyFile = tempDirectory.resolve("book-no-newline.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(9, keyMaterial.byteLength());
    }
  }

  @Test
  void load_rejectsEmptyPassphraseAfterTrailingLineEndingNormalization() throws Exception {
    Path keyFile = tempDirectory.resolve("empty.key");
    Files.writeString(keyFile, "\r\n", StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsSingleLineFeedThatNormalizesToEmptyPassphrase() throws Exception {
    Path keyFile = tempDirectory.resolve("line-feed-only.key");
    Files.writeString(keyFile, "\n", StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsTrulyEmptyKeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("empty-file.key");
    Files.write(keyFile, new byte[0]);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsInvalidUtf8() throws Exception {
    Path keyFile = tempDirectory.resolve("invalid-utf8.key");
    Files.write(keyFile, new byte[] {(byte) 0xC3, (byte) 0x28});

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a UTF-8 passphrase"));
  }

  @Test
  void load_rethrowsMissingKeyFileAsStableIllegalState() {
    Path keyFile = tempDirectory.resolve("missing.key");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("Failed to read the FinGrind book key file"));
  }
}
