package dev.erst.fingrind.sqlite;

/** Shared read-side delegation defaults for SQLite capability wrappers. */
interface SqliteReadCapabilityView
    extends SqliteReadSession,
        SqliteReadAccountCatalogCapabilityView,
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
}
