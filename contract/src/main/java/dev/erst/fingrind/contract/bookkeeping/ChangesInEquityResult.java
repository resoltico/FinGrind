package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;

/** Closed family of public statement-of-changes-in-equity outcomes. */
public sealed interface ChangesInEquityResult
    permits ChangesInEquityResult.Reported, ChangesInEquityResult.Rejected {

  /** Successful changes-in-equity result. */
  record Reported(ChangesInEquityReport report) implements ChangesInEquityResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }
  }

  /** Query rejection for one changes-in-equity request. */
  record Rejected(BookQueryRejection rejection) implements ChangesInEquityResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
