package dev.erst.fingrind.contract.payroll;

import java.util.Objects;
import java.util.regex.Pattern;

/** Opaque operational reference for one employee in one protected book. */
public record LatvianPayrollEmployeeReference(String value) {
  private static final Pattern CANONICAL_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,119}");

  /** Returns the canonical opaque employee-reference pattern. */
  public static String pattern() {
    return CANONICAL_PATTERN.pattern();
  }

  /** Returns the maximum canonical opaque employee-reference length. */
  public static int maxLength() {
    return 120;
  }

  /** Validates one non-personal-data payroll employee reference. */
  public LatvianPayrollEmployeeReference {
    Objects.requireNonNull(value, "value");
    if (!CANONICAL_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Latvian payroll employeeReference must be lower-kebab-case and at most 120 characters.");
    }
  }
}
