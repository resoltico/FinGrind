package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.policy.BookkeepingPolicyPack;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.BookkeepingReportStore;
import java.util.Objects;

/** Holds the canonical statement-computation seams for one selected bookkeeping book. */
final class BookkeepingStatementContext {
  private final BookLifecycleReader lifecycleReader;
  private final BookkeepingReportStore reportStore;
  private final BookkeepingPolicyPack policyPack;

  BookkeepingStatementContext(
      BookLifecycleReader lifecycleReader,
      BookkeepingReportStore reportStore,
      BookkeepingPolicyPack policyPack) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
    this.policyPack = BookkeepingPolicyPack.requirePolicyPack(policyPack);
  }

  BookkeepingReportStore reportStore() {
    return reportStore;
  }

  BookkeepingPolicyPack policyPack() {
    return policyPack;
  }

  BookIdentity bookIdentity() {
    return switch (lifecycleReader.inspectBook()) {
      case BookLifecycleInspection.Initialized initialized -> initialized.bookIdentity();
      case BookLifecycleInspection.Missing _ ->
          throw new IllegalStateException("Statement computation requires one initialized book.");
      case BookLifecycleInspection.Existing _ ->
          throw new IllegalStateException("Statement computation requires one initialized book.");
    };
  }
}
