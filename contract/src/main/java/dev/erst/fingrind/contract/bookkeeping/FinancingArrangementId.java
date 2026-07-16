package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Stable caller-chosen identifier for one financing arrangement lifecycle. */
public record FinancingArrangementId(String value) {
  private static final int MAX_LENGTH = 120;
  private static final String PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

  /** Validates one stable financing-arrangement identifier. */
  public FinancingArrangementId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Financing arrangement id must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Financing arrangement id must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Financing arrangement id must use lowercase kebab-case tokens.");
    }
  }
}
