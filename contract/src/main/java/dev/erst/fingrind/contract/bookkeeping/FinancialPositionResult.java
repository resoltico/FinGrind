package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Closed family of public statement-of-financial-position outcomes. */
public sealed interface FinancialPositionResult
    permits FinancialPositionResult.Reported, FinancialPositionResult.Rejected {

  /** Successful statement-of-financial-position result. */
  record Reported(FinancialPositionReport report) implements FinancialPositionResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }
  }

  /** Query rejection for one statement-of-financial-position request. */
  record Rejected(BookQueryRejection rejection) implements FinancialPositionResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
