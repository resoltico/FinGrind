package dev.erst.fingrind.jazzer.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** Owns one temporary replay directory and guarantees strict recursive cleanup on close. */
final class JazzerReplayScratchDirectory implements AutoCloseable {
  private final Path rootDirectory;
  private boolean closed;

  private JazzerReplayScratchDirectory(Path rootDirectory) {
    this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory must not be null");
  }

  static JazzerReplayScratchDirectory create(String prefix) throws IOException {
    return new JazzerReplayScratchDirectory(Files.createTempDirectory(prefix));
  }

  Path rootDirectory() {
    return rootDirectory;
  }

  Path resolve(Path relativePath) {
    Objects.requireNonNull(relativePath, "relativePath must not be null");
    if (relativePath.isAbsolute()) {
      throw new IllegalArgumentException("relativePath must be relative.");
    }
    return rootDirectory.resolve(relativePath);
  }

  @Override
  public void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    if (Files.notExists(rootDirectory)) {
      return;
    }
    try (var paths = Files.walk(rootDirectory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
