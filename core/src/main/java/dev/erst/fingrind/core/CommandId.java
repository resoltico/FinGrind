package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for one caller-visible posting command. */
public record CommandId(String value) {
  /** Validates a command identifier at the boundary where it is accepted or loaded. */
  public CommandId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Command id must not be blank.");
    }
  }
}
