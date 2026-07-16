package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Stable caller-chosen identifier for one foreign-currency receivable lifecycle. */
public record ForeignCurrencyObligationId(String value) {
  private static final int MAX_LENGTH = 120;
  private static final String PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

  /** Validates one stable foreign-currency-obligation identifier. */
  public ForeignCurrencyObligationId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Foreign-currency obligation id must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Foreign-currency obligation id must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Foreign-currency obligation id must use lowercase kebab-case tokens.");
    }
  }
}
