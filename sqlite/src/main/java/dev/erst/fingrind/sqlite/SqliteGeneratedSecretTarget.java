package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Owns the caller-visible absent-target precondition for generated secrets. */
final class SqliteGeneratedSecretTarget {
  private SqliteGeneratedSecretTarget() {}

  static void requireAbsent(Path finalPath) {
    Path checkedPath = Objects.requireNonNull(finalPath, "finalPath");
    if (Files.exists(checkedPath, LinkOption.NOFOLLOW_LINKS)) {
      throw new SqliteGeneratedSecretTargetOccupiedException(checkedPath);
    }
  }
}
