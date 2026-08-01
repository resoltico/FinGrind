package dev.erst.fingrind.cli.json;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Shared invariant helpers for CLI JSON model records. */
final class CliJsonModelValidation {
  private static final Pattern SHA_256_HEX = Pattern.compile("[0-9a-f]{64}");
  private static final Pattern CANONICAL_UNSIGNED_64_DECIMAL =
      Pattern.compile("0|[1-9][0-9]{0,19}");
  private static final String MAXIMUM_UNSIGNED_64_DECIMAL = "18446744073709551615";

  private CliJsonModelValidation() {}

  static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null.");
    String normalized = value.strip();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return normalized;
  }

  static @Nullable String requireOptionalText(@Nullable String value, String fieldName) {
    if (value == null) {
      return null;
    }
    return requireText(value, fieldName);
  }

  /** Requires one canonical lowercase SHA-256 digest value. */
  static String requireSha256Hex(String value, String fieldName) {
    String checked = requireText(value, fieldName);
    if (!SHA_256_HEX.matcher(checked).matches()) {
      throw new IllegalArgumentException(
          fieldName + " must contain 64 lowercase hexadecimal characters.");
    }
    return checked;
  }

  /** Requires one canonical unsigned decimal encoding in the unsigned 64-bit range. */
  static String requireCanonicalUnsigned64Decimal(String value, String fieldName) {
    String checked = requireText(value, fieldName);
    if (!CANONICAL_UNSIGNED_64_DECIMAL.matcher(checked).matches()
        || (checked.length() == MAXIMUM_UNSIGNED_64_DECIMAL.length()
            && MAXIMUM_UNSIGNED_64_DECIMAL.compareTo(checked) < 0)) {
      throw new IllegalArgumentException(
          fieldName + " must be a canonical unsigned 64-bit decimal string.");
    }
    return checked;
  }

  /** Requires one optional canonical lowercase SHA-256 digest value. */
  static @Nullable String requireOptionalSha256Hex(@Nullable String value, String fieldName) {
    return value == null ? null : requireSha256Hex(value, fieldName);
  }

  /** Requires one optional canonical unsigned 64-bit decimal value. */
  static @Nullable String requireOptionalCanonicalUnsigned64Decimal(
      @Nullable String value, String fieldName) {
    return value == null ? null : requireCanonicalUnsigned64Decimal(value, fieldName);
  }

  /** Compares two already-valid canonical unsigned 64-bit decimal values. */
  static int compareCanonicalUnsigned64Decimals(String left, String right) {
    String checkedLeft = requireCanonicalUnsigned64Decimal(left, "left");
    String checkedRight = requireCanonicalUnsigned64Decimal(right, "right");
    int lengthComparison = Integer.compare(checkedLeft.length(), checkedRight.length());
    return lengthComparison != 0 ? lengthComparison : checkedLeft.compareTo(checkedRight);
  }

  static <T> T requireValue(T value, String fieldName) {
    return Objects.requireNonNull(value, fieldName + " must not be null.");
  }

  static <T> List<T> copyList(List<T> values, String fieldName) {
    Objects.requireNonNull(values, fieldName + " must not be null.");
    for (int index = 0; index < values.size(); index++) {
      if (values.get(index) == null) {
        throw new IllegalArgumentException(fieldName + "[" + index + "] must not be null.");
      }
    }
    return List.copyOf(values);
  }

  static void requirePositive(int value, String fieldName) {
    if (value < 1) {
      throw new IllegalArgumentException(fieldName + " must be positive.");
    }
  }

  static void requireNonNegative(int value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must not be negative.");
    }
  }
}
