package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Closed family of inventory-valuation outcomes. */
public sealed interface InventoryValuationResult
    extends BookQueryReportResult<InventoryValuationReport>
    permits InventoryValuationResult.Reported, InventoryValuationResult.Rejected {
  /** Folds the closed result family without transport-layer pattern switching. */
  <T extends @Nullable Object> T fold(
      Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper);

  @Override
  default @Nullable InventoryValuationReport reported() {
    return fold(Reported::report, rejected -> null);
  }

  @Override
  default @Nullable BookQueryRejection rejection() {
    return fold(reported -> null, Rejected::rejection);
  }

  /** Successful exact inventory valuation. */
  record Reported(InventoryValuationReport report) implements InventoryValuationResult {
    public Reported {
      Objects.requireNonNull(report, "report");
    }

    @Override
    public <T extends @Nullable Object> T fold(
        Function<Reported, T> reportedMapper, Function<Rejected, T> rejectedMapper) {
      return Objects.requireNonNull(reportedMapper, "reportedMapper").apply(this);
    }
  }

  /** Deterministic query refusal. */
  record Rejected(BookQueryRejection rejection) implements InventoryValuationResult {
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
