package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Closed family of public income-statement outcomes. */
public sealed interface IncomeStatementResult
    permits IncomeStatementResult.Reported, IncomeStatementResult.Rejected {

  /** Successful income-statement result. */
  record Reported(IncomeStatementReport report) implements IncomeStatementResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }
  }

  /** Query rejection for one income-statement request. */
  record Rejected(BookQueryRejection rejection) implements IncomeStatementResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
