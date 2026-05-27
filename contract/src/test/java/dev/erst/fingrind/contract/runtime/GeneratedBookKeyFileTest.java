package dev.erst.fingrind.contract.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Coverage tests for generated book-key metadata. */
class GeneratedBookKeyFileTest {
  @TempDir Path tempDir;

  @Test
  void generatedBookKeyMetadataAcceptsRegularFilesAndFuturePaths() throws Exception {
    Path existingFile = Files.createFile(tempDir.resolve("book.key"));
    GeneratedBookKeyFile existing =
        new GeneratedBookKeyFile(existingFile, "base64", 256, "rw-------");
    GeneratedBookKeyFile future =
        new GeneratedBookKeyFile(tempDir.resolve("future.key"), "base64", 256, "rw-------");

    assertEquals(existingFile, existing.bookKeyFilePath());
    assertEquals("rw-------", future.permissions());
  }

  @Test
  void generatedBookKeyMetadataRejectsDirectoriesAndInvalidFields() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedBookKeyFile(tempDir, "base64", 256, "rw-------"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedBookKeyFile(tempDir.resolve("book.key"), " ", 256, "rw-------"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedBookKeyFile(tempDir.resolve("book.key"), "base64", 0, "rw-------"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new GeneratedBookKeyFile(tempDir.resolve("book.key"), "base64", 256, " "));
  }
}
