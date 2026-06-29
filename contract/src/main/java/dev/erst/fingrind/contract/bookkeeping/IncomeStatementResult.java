package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Closed family of public income-statement outcomes. */
public sealed interface IncomeStatementResult
    permits IncomeStatementResult.Reported, IncomeStatementResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  /** Successful income-statement result. */
  record Reported(IncomeStatementReport report) implements IncomeStatementResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Query rejection for one income-statement request. */
  record Rejected(BookQueryRejection rejection) implements IncomeStatementResult {
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
