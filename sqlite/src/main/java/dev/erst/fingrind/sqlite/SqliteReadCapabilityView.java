package dev.erst.fingrind.sqlite;

/** Shared read-side delegation defaults for SQLite capability wrappers. */
interface SqliteReadCapabilityView
    extends SqliteReadSession,
        SqliteReadAccountCatalogCapabilityView,
        SqliteReadTaxCatalogCapabilityView,
        SqliteReadPostingLookupCapabilityView,
        SqliteReadReportingCapabilityView {
  @Override
  default dev.erst.fingrind.executor.spi.BookLifecycleInspection inspectBook() {
    return SqliteReadAccountCatalogCapabilityView.super.inspectBook();
  }

  @Override
  default boolean allowsInitializedWorkflow() {
    return SqliteReadAccountCatalogCapabilityView.super.allowsInitializedWorkflow();
  }

  @Override
  default dev.erst.fingrind.core.BookIdentity requireInitializedBookIdentity() {
    return SqliteReadAccountCatalogCapabilityView.super.requireInitializedBookIdentity();
  }

  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.CommittedPosting> postings(
      dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
    return SqliteReadReportingCapabilityView.super.postings(effectiveDateRange);
  }

  @Override
  default java.util.Optional<java.time.LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  @Override
  default java.util.Optional<java.time.LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }
}
