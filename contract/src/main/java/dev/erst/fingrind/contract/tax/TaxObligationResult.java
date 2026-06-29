package dev.erst.fingrind.contract.tax;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Closed family of public tax-obligation outcomes. */
public sealed interface TaxObligationResult
    permits TaxObligationResult.Reported, TaxObligationResult.Rejected {

  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  /** Successful tax-obligation result. */
  record Reported(TaxObligationReport report) implements TaxObligationResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Query rejection for one tax-obligation request. */
  record Rejected(TaxQueryRejection rejection) implements TaxObligationResult {
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
