package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import java.util.Objects;

/** Closed result family for comprehensive-income reporting. */
public sealed interface ComprehensiveIncomeResult
    permits ComprehensiveIncomeResult.Computed, ComprehensiveIncomeResult.Rejected {
  /** Successful report computation. */
  record Computed(ComprehensiveIncomeReport report) implements ComprehensiveIncomeResult {
    public Computed {
      Objects.requireNonNull(report, "report");
    }
  }

  /** Deterministic refusal. */
  record Rejected(BookQueryRejection rejection) implements ComprehensiveIncomeResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
