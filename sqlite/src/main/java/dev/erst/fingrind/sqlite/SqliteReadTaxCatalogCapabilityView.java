package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import dev.erst.fingrind.executor.spi.TaxRegistrationCatalogStore;
import java.util.List;
import java.util.Optional;

/** Shared tax-registration catalog defaults for SQLite read wrappers. */
interface SqliteReadTaxCatalogCapabilityView
    extends TaxRegistrationCatalogStore, SqliteLifecycleInspectionCapabilityView {
  @Override
  default Optional<DeclaredTaxRegistration> findTaxRegistration(
      TaxRegistrationId taxRegistrationId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().taxRegistrations().findTaxRegistration(taxRegistrationId);
  }

  @Override
  default List<DeclaredTaxRegistration> allTaxRegistrations() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().taxRegistrations().allTaxRegistrations();
  }

  @Override
  default TaxRegistrationPage listTaxRegistrations(ListTaxRegistrationsQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().taxRegistrations().listTaxRegistrations(query);
  }
}
