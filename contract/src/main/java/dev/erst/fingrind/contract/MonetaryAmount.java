package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import java.util.Objects;

/** Public machine-facing exact money shape carried across FinGrind contracts. */
public record MonetaryAmount(String currencyCode, String minorUnits) {
  /** Validates one canonical machine-facing exact money shape. */
  public MonetaryAmount {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Objects.requireNonNull(minorUnits, "minorUnits");
    if (minorUnits.isBlank()) {
      throw new IllegalArgumentException("minorUnits must not be blank.");
    }
    if (!minorUnits.chars().allMatch(character -> character >= '0' && character <= '9')) {
      throw new IllegalArgumentException("minorUnits must contain ASCII decimal digits only.");
    }
    if (minorUnits.length() > 1 && minorUnits.startsWith("0")) {
      throw new IllegalArgumentException("minorUnits must not contain redundant leading zeroes.");
    }
    if (minorUnits.length() > Money.maxMinorUnitsDigitCount()) {
      throw new IllegalArgumentException("minorUnits is outside the supported exact money range.");
    }
    CurrencyUnit.of(currencyCode);
    parseMinorUnits(minorUnits);
  }

  /** Creates one machine-facing exact money shape from the core money value object. */
  public static MonetaryAmount of(Money money) {
    Objects.requireNonNull(money, "money");
    return new MonetaryAmount(money.currencyUnit().code(), Long.toString(money.minorUnits()));
  }

  /** Converts this public exact money shape back into the core money value object. */
  public Money toMoney() {
    return Money.ofMinorUnits(CurrencyUnit.of(currencyCode), parseMinorUnits(minorUnits));
  }

  /** Returns the canonical decimal string derived from the exact amount and scale. */
  public String canonicalDecimal() {
    return toMoney().canonicalDecimal();
  }

  private static long parseMinorUnits(String text) {
    try {
      return Long.parseLong(text);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "minorUnits is outside the supported exact money range.", exception);
    }
  }
}
