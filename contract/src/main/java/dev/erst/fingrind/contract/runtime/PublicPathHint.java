package dev.erst.fingrind.contract.runtime;

import java.nio.file.Path;
import java.util.Objects;

/** Redacted public path hint that preserves only the last path segment. */
public record PublicPathHint(String value) {
  /** Validates one redacted public path hint. */
  public PublicPathHint {
    Objects.requireNonNull(value, "value");
    if (!"<redacted>".equals(value) && !value.startsWith("<redacted>/")) {
      throw new IllegalArgumentException(
          "Public path hints must equal <redacted> or start with <redacted>/.");
    }
  }

  /** Returns the canonical redacted hint for one filesystem path. */
  public static PublicPathHint fromPath(Path path) {
    Path normalizedPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    Path fileName = normalizedPath.getFileName();
    return new PublicPathHint(fileName == null ? "<redacted>" : "<redacted>/" + fileName);
  }
}
