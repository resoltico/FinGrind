package dev.erst.fingrind.contract.bookkeeping;

import java.time.DateTimeException;
import java.time.Year;

/** Administrative command that closes one fiscal year identified by its label. */
public record FiscalYearCloseCommand(int fiscalYearLabel) {
  /** Validates one fiscal-year-close command. */
  public FiscalYearCloseCommand {
    try {
      Year.of(fiscalYearLabel);
    } catch (DateTimeException exception) {
      throw new IllegalArgumentException(
          "fiscalYearLabel must be one supported ISO-8601 proleptic year.", exception);
    }
  }
}
