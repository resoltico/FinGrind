package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable identifier for the actor that submitted one posting request. */
public record ActorId(String value) {
  /** Validates an actor identifier at the boundary where it is accepted or loaded. */
  public ActorId {
    Objects.requireNonNull(value, "value");
    value = value.strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Actor id must not be blank.");
    }
  }
}
