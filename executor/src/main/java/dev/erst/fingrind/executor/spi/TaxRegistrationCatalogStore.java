package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.ListTaxRegistrationsQuery;
import dev.erst.fingrind.contract.tax.TaxRegistrationPage;
import java.util.List;

/** Reads ordered tax-registration catalog views from the selected book. */
public interface TaxRegistrationCatalogStore extends TaxRegistrationLookupStore {
  /** Returns the declared tax registrations in one stable in-memory order. */
  List<DeclaredTaxRegistration> allTaxRegistrations();

  /** Returns one paginated slice of the declared tax-registration registry. */
  TaxRegistrationPage listTaxRegistrations(ListTaxRegistrationsQuery query);
}
