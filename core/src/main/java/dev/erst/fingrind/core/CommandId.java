package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.UUID;

/** Canonical UUID identifier for one caller-visible posting command. */
public record CommandId(String value) {
  /** Validates a command identifier at the boundary where it is accepted or loaded. */
  public CommandId {
    try {
      value = UUID.fromString(Objects.requireNonNull(value, "value").strip()).toString();
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Command id must be one canonical UUID.", exception);
    }
  }
}
