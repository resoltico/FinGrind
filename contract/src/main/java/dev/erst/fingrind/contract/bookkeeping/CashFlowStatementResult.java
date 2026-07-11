package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Closed family of public cash-flow-statement outcomes. */
public sealed interface CashFlowStatementResult
    extends BookQueryReportResult<CashFlowStatementReport>
    permits CashFlowStatementResult.Reported, CashFlowStatementResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  @Override
  default @Nullable CashFlowStatementReport reported() {
    return fold(Reported::report, rejected -> null);
  }

  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Successful cash-flow-statement result. */
  record Reported(CashFlowStatementReport report) implements CashFlowStatementResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Query rejection for one cash-flow-statement request. */
  record Rejected(BookQueryRejection rejection) implements CashFlowStatementResult {
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
