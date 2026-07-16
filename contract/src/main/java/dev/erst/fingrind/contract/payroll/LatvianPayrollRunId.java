package dev.erst.fingrind.contract.payroll;

import java.util.Objects;

/** Stable caller-chosen identifier for one Latvian monthly payroll run. */
public record LatvianPayrollRunId(String value) {
  private static final int MAX_LENGTH = 120;
  private static final String PATTERN = "[a-z0-9]+(?:-[a-z0-9]+)*";

  /** Returns the canonical payroll-run identifier pattern. */
  public static String pattern() {
    return PATTERN;
  }

  /** Returns the maximum canonical payroll-run identifier length. */
  public static int maxLength() {
    return MAX_LENGTH;
  }

  /** Validates one stable payroll-run identifier. */
  public LatvianPayrollRunId {
    value = Objects.requireNonNull(value, "value").strip();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Latvian payroll run id must not be blank.");
    }
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "Latvian payroll run id must not exceed %d characters.".formatted(MAX_LENGTH));
    }
    if (!value.matches(PATTERN)) {
      throw new IllegalArgumentException(
          "Latvian payroll run id must use lowercase kebab-case tokens.");
    }
  }
}
