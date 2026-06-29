package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Closed family of public statement-of-financial-position outcomes. */
public sealed interface FinancialPositionResult
    permits FinancialPositionResult.Reported, FinancialPositionResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  /** Successful statement-of-financial-position result. */
  record Reported(FinancialPositionReport report) implements FinancialPositionResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Query rejection for one statement-of-financial-position request. */
  record Rejected(BookQueryRejection rejection) implements FinancialPositionResult {
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
