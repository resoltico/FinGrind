package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioral tests for bounded nofollow reads and writes of FinGrind-owned recovery records. */
class SqliteSecureRegularFileAccessTest extends SqliteNativeBridgeTestSupport {
  @Test
  void readsOwnerOnlyMetadataThroughBoundedByteAndLineContracts() throws Exception {
    Path record = tempDirectory.resolve("secure-access/record.control");
    Path parent = record.getParent();
    if (parent == null) {
      throw new AssertionError("Recovery-record fixture requires one parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    try (FileChannel channel = SqliteSecureRegularFileAccess.openNewWrite(record)) {
      channel.write(
          ByteBuffer.wrap("first\nsecond\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    assertEquals(List.of("first", "second"), SqliteSecureRegularFileAccess.readUtf8Lines(record));
    assertEquals(
        List.of("first", "second"),
        SqliteSecureRegularFileAccess.readUtf8LinesBounded(record, 32, 2));
    IOException tooManyLines =
        assertThrows(
            IOException.class,
            () -> SqliteSecureRegularFileAccess.readUtf8LinesBounded(record, 32, 1));
    assertTrue(
        java.util.Objects.requireNonNull(tooManyLines.getMessage(), "line-bound message")
            .contains("too many lines"));
    IOException tooManyBytes =
        assertThrows(
            IOException.class, () -> SqliteSecureRegularFileAccess.readAllBytesBounded(record, 2));
    assertTrue(
        java.util.Objects.requireNonNull(tooManyBytes.getMessage(), "byte-bound message")
            .contains("maximum allowed size"));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteSecureRegularFileAccess.readAllBytesBounded(record, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> SqliteSecureRegularFileAccess.readUtf8LinesBounded(record, 8, Integer.MAX_VALUE));
  }

  @Test
  void writeAndForceOperationsRequireOneExactRegularFile() throws Exception {
    Path record = tempDirectory.resolve("secure-write/record.control");
    Path parent = record.getParent();
    if (parent == null) {
      throw new AssertionError("Secure-write fixture requires one parent directory.");
    }
    Files.createDirectories(parent);
    SqliteTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(parent);
    try (FileChannel channel = SqliteSecureRegularFileAccess.openNewWrite(record)) {
      channel.write(ByteBuffer.wrap(new byte[] {1, 2, 3}));
    }
    SqliteSecureRegularFileAccess.forceFile(record);
    try (FileChannel channel = SqliteSecureRegularFileAccess.openTruncatingWrite(record)) {
      channel.write(ByteBuffer.wrap(new byte[] {9}));
    }
    assertEquals(1L, Files.size(record));
    assertEquals(9, SqliteSecureRegularFileAccess.readAllBytes(record)[0]);

    Path directory = Files.createDirectory(parent.resolve("not-a-regular-file"));
    IOException refusal =
        assertThrows(IOException.class, () -> SqliteSecureRegularFileAccess.openRead(directory));
    assertTrue(
        java.util.Objects.requireNonNull(refusal.getMessage(), "non-regular refusal message")
            .contains("regular non-symlink"));
  }
}
