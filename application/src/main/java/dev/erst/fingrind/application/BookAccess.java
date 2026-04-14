package dev.erst.fingrind.application;

import java.nio.file.Path;
import java.util.Objects;

/** One durable book file plus the key file required to access it. */
public record BookAccess(Path bookFilePath, Path bookKeyFilePath) {
  public BookAccess {
    Objects.requireNonNull(bookFilePath, "bookFilePath");
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
  }
}
