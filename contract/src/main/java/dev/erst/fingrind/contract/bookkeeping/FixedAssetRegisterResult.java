package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Result of one fixed-asset register query. */
public sealed interface FixedAssetRegisterResult
    extends BookQueryReportResult<FixedAssetRegisterReport>
    permits FixedAssetRegisterResult.Reported, FixedAssetRegisterResult.Rejected {
  /** Folds the closed result family. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  @Override
  default @Nullable FixedAssetRegisterReport reported() {
    return fold(Reported::report, rejected -> null);
  }

  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Successful register projection. */
  record Reported(FixedAssetRegisterReport report) implements FixedAssetRegisterResult {
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
  record Rejected(BookQueryRejection rejection) implements FixedAssetRegisterResult {
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
