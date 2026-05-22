package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical lowercase SHA-256 hex digest retained for one evidence artifact. */
public record ContentSha256(String value) {
  private static final String PATTERN = "^[0-9a-f]{64}$";

  /** Validates one lowercase SHA-256 hex digest. */
  public ContentSha256 {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException("Content SHA-256 must use 64 lowercase hex characters.");
    }
  }
}
