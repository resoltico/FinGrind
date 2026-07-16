package dev.erst.fingrind.contract.payroll;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/** One Latvian payroll accounting month expressed as an ISO-8601 year-month. */
public record LatvianPayrollMonth(YearMonth value) {
  private static final String WIRE_PATTERN = "[0-9]{4}-(0[1-9]|1[0-2])";

  /** Returns the canonical payroll-month wire pattern. */
  public static String wirePattern() {
    return WIRE_PATTERN;
  }

  /** Returns the canonical payroll-month wire length. */
  public static int wireLength() {
    return 7;
  }

  /** Validates one payroll month. */
  public LatvianPayrollMonth {
    Objects.requireNonNull(value, "value");
  }

  /** Parses one canonical {@code YYYY-MM} payroll month. */
  public static LatvianPayrollMonth parse(String text) {
    Objects.requireNonNull(text, "text");
    try {
      return new LatvianPayrollMonth(YearMonth.parse(text, DateTimeFormatter.ofPattern("uuuu-MM")));
    } catch (DateTimeParseException exception) {
      throw new IllegalArgumentException("Payroll month must use YYYY-MM.", exception);
    }
  }

  /** Returns the canonical wire representation. */
  public String wireValue() {
    return value.format(DateTimeFormatter.ofPattern("uuuu-MM"));
  }
}
