package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable retained locator for one evidence artifact payload. */
public record StorageLocator(String value) {
  private static final int MAX_LENGTH = 512;

  /** Validates one retained storage locator. */
  public StorageLocator {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Storage locator must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Storage locator must not exceed %d characters.".formatted(MAX_LENGTH));
    }
  }
}
