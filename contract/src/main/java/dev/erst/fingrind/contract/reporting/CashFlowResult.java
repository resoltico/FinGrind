package dev.erst.fingrind.contract.reporting;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import java.util.Objects;

/** Closed result family for cash-flow reporting. */
public sealed interface CashFlowResult permits CashFlowResult.Computed, CashFlowResult.Rejected {
  /** Successful report computation. */
  record Computed(CashFlowReport report) implements CashFlowResult {
    public Computed {
      Objects.requireNonNull(report, "report");
    }
  }

  /** Deterministic refusal. */
  record Rejected(BookQueryRejection rejection) implements CashFlowResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
