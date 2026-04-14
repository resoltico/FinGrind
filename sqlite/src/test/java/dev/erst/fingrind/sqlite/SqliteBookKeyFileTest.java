package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for the SQLite book-key file loader. */
class SqliteBookKeyFileTest {
  @TempDir Path tempDirectory;

  @Test
  void load_acceptsUtf8PassphraseAndStripsOneTrailingLineEnding() throws Exception {
    Path keyFile = tempDirectory.resolve("book.key");
    writeSecureString(keyFile, "swordfish\n");

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
    writeSecureString(keyFile, "swordfish\r\n");

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(9, keyMaterial.byteLength());
    }
  }

  @Test
  void load_acceptsUtf8PassphraseWithoutTrailingLineEnding() throws Exception {
    Path keyFile = tempDirectory.resolve("book-no-newline.key");
    writeSecureString(keyFile, "swordfish");

    try (SqliteBookPassphrase keyMaterial = SqliteBookKeyFile.load(keyFile)) {
      assertEquals(9, keyMaterial.byteLength());
    }
  }

  @Test
  void load_rejectsEmptyPassphraseAfterTrailingLineEndingNormalization() throws Exception {
    Path keyFile = tempDirectory.resolve("empty.key");
    writeSecureString(keyFile, "\r\n");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsSingleLineFeedThatNormalizesToEmptyPassphrase() throws Exception {
    Path keyFile = tempDirectory.resolve("line-feed-only.key");
    writeSecureString(keyFile, "\n");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsTrulyEmptyKeyFile() throws Exception {
    Path keyFile = tempDirectory.resolve("empty-file.key");
    writeSecureBytes(keyFile, new byte[0]);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a non-empty UTF-8 passphrase"));
  }

  @Test
  void load_rejectsInvalidUtf8() throws Exception {
    Path keyFile = tempDirectory.resolve("invalid-utf8.key");
    writeSecureBytes(keyFile, new byte[] {(byte) 0xC3, (byte) 0x28});

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("must contain a UTF-8 passphrase"));
  }

  @Test
  void load_rejectsControlCharactersInsidePassphrase() throws Exception {
    Path keyFile = tempDirectory.resolve("control-character.key");
    writeSecureString(keyFile, "sword\u0007fish");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(
        exception
            .getMessage()
            .contains(
                "must contain a single-line UTF-8 text passphrase without control characters"));
  }

  @Test
  void load_rejectsEmbeddedLineFeedsAfterTrailingNormalization() throws Exception {
    Path keyFile = tempDirectory.resolve("embedded-line-feed.key");
    writeSecureString(keyFile, "first\nsecond\n");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(
        exception
            .getMessage()
            .contains(
                "must contain a single-line UTF-8 text passphrase without control characters"));
  }

  @Test
  void load_rethrowsMissingKeyFileAsStableIllegalState() {
    Path keyFile = tempDirectory.resolve("missing.key");

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("Failed to read the FinGrind book key file"));
  }

  @Test
  void load_rejectsDirectoryTargets() throws IOException {
    Path keyDirectory = tempDirectory.resolve("not-a-file.key");
    Files.createDirectories(keyDirectory);

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyDirectory));

    assertTrue(exception.getMessage().contains("regular non-symlink file"));
  }

  @Test
  void load_rejectsOwnerUnreadableKeyFiles() throws IOException {
    Path keyFile = tempDirectory.resolve("owner-unreadable.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(keyFile, Set.of(PosixFilePermission.OWNER_WRITE));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("owner-readable"));
  }

  @Test
  void load_rejectsGroupReadableKeyFiles() throws IOException {
    Path keyFile = tempDirectory.resolve("group-readable.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);
    Files.setPosixFilePermissions(
        keyFile,
        Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.GROUP_READ));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> SqliteBookKeyFile.load(keyFile));

    assertTrue(exception.getMessage().contains("owner-only permissions"));
  }

  @Test
  void requireSecureKeyFile_wrapsUnsupportedPosixFilesystem() throws IOException {
    Path keyFile = tempDirectory.resolve("zipfs.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFile.requireSecureKeyFile(
                    keyFile,
                    path -> {
                      throw new UnsupportedOperationException("no posix");
                    }));

    assertTrue(exception.getMessage().contains("must live on a POSIX filesystem"));
  }

  @Test
  void requireSecureKeyFile_wrapsPermissionInspectionIoFailures() throws IOException {
    Path keyFile = tempDirectory.resolve("permission-io.key");
    Files.writeString(keyFile, "swordfish", StandardCharsets.UTF_8);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteBookKeyFile.requireSecureKeyFile(
                    keyFile,
                    path -> {
                      throw new IOException("boom");
                    }));

    assertTrue(
        exception
            .getMessage()
            .contains("Failed to inspect the FinGrind book key file permissions"));
  }

  private static void writeSecureString(Path keyFile, String content) throws IOException {
    Files.writeString(keyFile, content, StandardCharsets.UTF_8);
    secure(keyFile);
  }

  private static void writeSecureBytes(Path keyFile, byte[] content) throws IOException {
    Files.write(keyFile, content);
    secure(keyFile);
  }

  private static void secure(Path keyFile) throws IOException {
    Files.setPosixFilePermissions(
        keyFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
  }
}
