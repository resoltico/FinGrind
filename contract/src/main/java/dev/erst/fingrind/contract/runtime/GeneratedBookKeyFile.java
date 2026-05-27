package dev.erst.fingrind.contract.runtime;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/** Non-secret metadata describing one newly created protected-book key file. */
public record GeneratedBookKeyFile(
    Path bookKeyFilePath, String encoding, int entropyBits, String permissions) {
  public GeneratedBookKeyFile {
    Objects.requireNonNull(bookKeyFilePath, "bookKeyFilePath");
    if (Files.exists(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)
        && !Files.isRegularFile(bookKeyFilePath, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("bookKeyFilePath must identify a regular file.");
    }
    Objects.requireNonNull(encoding, "encoding");
    if (encoding.isBlank()) {
      throw new IllegalArgumentException("encoding must not be blank.");
    }
    if (entropyBits <= 0) {
      throw new IllegalArgumentException("entropyBits must be positive.");
    }
    Objects.requireNonNull(permissions, "permissions");
    if (permissions.isBlank()) {
      throw new IllegalArgumentException("permissions must not be blank.");
    }
  }
}
