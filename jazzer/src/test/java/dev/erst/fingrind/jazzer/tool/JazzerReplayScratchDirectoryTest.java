package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Covers temporary replay scratch-directory ownership and cleanup rules. */
class JazzerReplayScratchDirectoryTest {
  @Test
  void close_removesCreatedTree() throws IOException {
    Path rootDirectory;
    try (JazzerReplayScratchDirectory scratchDirectory =
        JazzerReplayScratchDirectory.create("fingrind-jazzer-test-")) {
      rootDirectory = scratchDirectory.rootDirectory();
      Path nestedFile = scratchDirectory.resolve(Path.of("nested", "seed.json"));
      Files.createDirectories(nestedFile.getParent());
      Files.writeString(nestedFile, "{}");

      assertTrue(Files.exists(nestedFile));
    }

    assertFalse(Files.exists(rootDirectory));
  }

  @Test
  void resolve_rejectsAbsolutePaths_and_close_toleratesMissingTree() throws IOException {
    Path rootDirectory;
    try (JazzerReplayScratchDirectory scratchDirectory =
        JazzerReplayScratchDirectory.create("fingrind-jazzer-test-")) {
      rootDirectory = scratchDirectory.rootDirectory();
      Path absolutePath = rootDirectory.toAbsolutePath();

      assertThrows(IllegalArgumentException.class, () -> scratchDirectory.resolve(absolutePath));

      Files.delete(rootDirectory);
      scratchDirectory.close();
    }
    assertFalse(Files.exists(rootDirectory));
  }
}
