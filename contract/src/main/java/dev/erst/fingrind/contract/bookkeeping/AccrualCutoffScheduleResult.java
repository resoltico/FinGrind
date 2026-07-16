package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Result of one accrual cut-off schedule query. */
public sealed interface AccrualCutoffScheduleResult
    extends BookQueryReportResult<AccrualCutoffScheduleReport>
    permits AccrualCutoffScheduleResult.Reported, AccrualCutoffScheduleResult.Rejected {
  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  /** Returns the report when the query was admitted, otherwise null. */
  @Override
  default @Nullable AccrualCutoffScheduleReport reported() {
    return fold(Reported::report, rejected -> null);
  }

  /** Returns the deterministic query rejection when the query did not succeed. */
  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Successful schedule projection. */
  record Reported(AccrualCutoffScheduleReport report) implements AccrualCutoffScheduleResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Deterministic read-side refusal. */
  record Rejected(BookQueryRejection rejection) implements AccrualCutoffScheduleResult {
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
