package dev.erst.fingrind.sqlite;

/** Shared administration delegation defaults for SQLite capability wrappers. */
interface SqliteAdministrationCapabilityView
    extends SqliteAdministrationSession,
        SqliteReadAccountCatalogCapabilityView,
        SqliteReadTaxCatalogCapabilityView,
        SqliteAttestedAdministrationMutationView {

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
