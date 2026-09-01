package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.SignedMoney;
import java.util.Objects;

/** Public machine-facing exact signed money shape for report adjustments and net positions. */
public record SignedMonetaryAmount(String currencyCode, String minorUnits) {
  /** Validates one canonical machine-facing exact signed money shape. */
  public SignedMonetaryAmount {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Objects.requireNonNull(minorUnits, "minorUnits");
    if (minorUnits.isBlank()) {
      throw new IllegalArgumentException("minorUnits must not be blank.");
    }
    String magnitude = minorUnits.startsWith("-") ? minorUnits.substring(1) : minorUnits;
    if (minorUnits.startsWith("+")) {
      throw new IllegalArgumentException("minorUnits must not include a plus sign.");
    }
    if (magnitude.isEmpty() || magnitude.indexOf('.') >= 0) {
      throw new IllegalArgumentException(
          "minorUnits must be one exact minor-unit integer and must not include a decimal point.");
    }
    if (!magnitude.chars().allMatch(character -> character >= '0' && character <= '9')) {
      throw new IllegalArgumentException(
          "minorUnits must contain ASCII decimal digits and an optional minus sign.");
    }
    if (magnitude.length() > 1 && magnitude.startsWith("0")) {
      throw new IllegalArgumentException("minorUnits must not contain redundant leading zeroes.");
    }
    if ("-0".equals(minorUnits)) {
      throw new IllegalArgumentException("minorUnits must represent zero without a minus sign.");
    }
    CurrencyUnit.of(currencyCode);
    parseMinorUnits(minorUnits);
  }

  /** Creates one machine-facing signed amount from the core value object. */
  public static SignedMonetaryAmount of(SignedMoney money) {
    Objects.requireNonNull(money, "money");
    return new SignedMonetaryAmount(money.currencyUnit().code(), Long.toString(money.minorUnits()));
  }

  /** Converts this public shape to its core signed-money value. */
  public SignedMoney toSignedMoney() {
    return SignedMoney.ofMinorUnits(CurrencyUnit.of(currencyCode), parseMinorUnits(minorUnits));
  }

  /** Returns canonical fixed-scale decimal text. */
  public String canonicalDecimal() {
    return toSignedMoney().canonicalDecimal();
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
