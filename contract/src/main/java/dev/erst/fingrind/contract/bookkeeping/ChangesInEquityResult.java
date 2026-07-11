package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Closed family of public statement-of-changes-in-equity outcomes. */
public sealed interface ChangesInEquityResult extends BookQueryReportResult<ChangesInEquityReport>
    permits ChangesInEquityResult.Reported, ChangesInEquityResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  @Override
  default @Nullable ChangesInEquityReport reported() {
    return fold(Reported::report, rejected -> null);
  }

  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Successful changes-in-equity result. */
  record Reported(ChangesInEquityReport report) implements ChangesInEquityResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Query rejection for one changes-in-equity request. */
  record Rejected(BookQueryRejection rejection) implements ChangesInEquityResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(rejectedMapper, "rejectedMapper").apply(this);
    }
  }
}
