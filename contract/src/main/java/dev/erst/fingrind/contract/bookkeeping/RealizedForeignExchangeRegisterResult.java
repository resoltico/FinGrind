package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Result of one realized foreign-exchange register query. */
public sealed interface RealizedForeignExchangeRegisterResult
    extends BookQueryReportResult<RealizedForeignExchangeRegisterReport>
    permits RealizedForeignExchangeRegisterResult.Reported,
        RealizedForeignExchangeRegisterResult.Rejected {
  /** Folds the closed result family. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  @Override
  default @Nullable RealizedForeignExchangeRegisterReport reported() {
    return fold(Reported::report, rejected -> null);
  }

  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Successful realized foreign-exchange register projection. */
  record Reported(RealizedForeignExchangeRegisterReport report)
      implements RealizedForeignExchangeRegisterResult {
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
  record Rejected(BookQueryRejection rejection) implements RealizedForeignExchangeRegisterResult {
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
