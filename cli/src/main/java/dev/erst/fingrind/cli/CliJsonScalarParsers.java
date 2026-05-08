package dev.erst.fingrind.cli;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Scalar and wire-value parsers for CLI JSON request fields. */
final class CliJsonScalarParsers {
  private CliJsonScalarParsers() {}

  static BigDecimal parseAmount(String amountText) {
    if (amountText.indexOf('e') >= 0 || amountText.indexOf('E') >= 0) {
      throw new IllegalArgumentException(
          "Money amount must be a plain decimal string without exponent notation.");
    }
    try {
      return new BigDecimal(amountText);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Money amount must be a valid decimal string.", exception);
    }
  }

  static <T> T parseWireValue(
      String rawValue, String fieldName, List<String> acceptedValues, Function<String, T> parser) {
    try {
      return parser.apply(rawValue);
    } catch (IllegalArgumentException exception) {
      throw unsupportedValue(fieldName, rawValue, acceptedValues, exception);
    }
  }

  static IllegalArgumentException unsupportedValue(
      String fieldName, String rawValue, List<String> acceptedValues, @Nullable Throwable cause) {
    return new IllegalArgumentException(
        "Unsupported value for "
            + fieldName
            + ": "
            + rawValue
            + ". Accepted values: "
            + String.join(", ", acceptedValues)
            + ".",
        cause);
  }
}
