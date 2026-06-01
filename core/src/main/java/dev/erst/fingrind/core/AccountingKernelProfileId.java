package dev.erst.fingrind.core;

import java.util.Objects;

/** Stable executable doctrine/profile identifier for one bookkeeping kernel line. */
public record AccountingKernelProfileId(String value) {
  private static final int MAX_LENGTH = 120;

  /** Validates one stable profile identifier. */
  public AccountingKernelProfileId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Accounting kernel profile id must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Accounting kernel profile id must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
      throw new IllegalArgumentException(
          "Accounting kernel profile id must use lowercase kebab-case tokens.");
    }
  }
}
