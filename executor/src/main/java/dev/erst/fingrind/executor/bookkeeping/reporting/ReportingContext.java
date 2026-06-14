package dev.erst.fingrind.executor.bookkeeping.reporting;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRules;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.BookkeepingReportStore;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Holds the canonical statement-computation seams for one selected bookkeeping book. */
final class ReportingContext {
  private final BookLifecycleReader lifecycleReader;
  private final BookkeepingReportStore reportStore;

  ReportingContext(BookLifecycleReader lifecycleReader, BookkeepingReportStore reportStore) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.reportStore = Objects.requireNonNull(reportStore, "reportStore");
  }

  BookkeepingReportStore reportStore() {
    return reportStore;
  }

  KernelAccountingRules accountingRules() {
    return KernelAccountingRulesResolver.forBookIdentity(bookIdentity());
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

  Optional<LocalDate> resolvedEffectiveDateAsOf(Optional<LocalDate> selectedEffectiveDateAsOf) {
    Objects.requireNonNull(selectedEffectiveDateAsOf, "selectedEffectiveDateAsOf");
    return selectedEffectiveDateAsOf.isPresent()
        ? selectedEffectiveDateAsOf
        : reportStore.latestPostingEffectiveDate();
  }
}
