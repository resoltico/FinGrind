package dev.erst.fingrind.cli;

import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Scalar and wire-value parsers for CLI JSON request fields. */
final class CliJsonScalarParsers {
  private CliJsonScalarParsers() {}

  static <T> T parseWireValue(
      String rawValue, String fieldName, List<String> acceptedValues, Function<String, T> parser) {
    if (!acceptedValues.contains(rawValue)) {
      throw unsupportedValue(fieldName, rawValue, acceptedValues, null);
    }
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
