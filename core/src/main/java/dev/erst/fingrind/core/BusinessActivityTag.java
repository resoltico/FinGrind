package dev.erst.fingrind.core;

import java.util.Objects;

/** One neutral business-activity tag attached to an accounting entity profile. */
public record BusinessActivityTag(String value) {
  /** Validates one stable activity tag. */
  public BusinessActivityTag {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("value must not be blank.");
    }
  }
}
