package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import java.util.Optional;

/** Looks up declared tax registrations by stable semantic identity. */
@FunctionalInterface
public interface TaxRegistrationLookupStore {
  /** Looks up one declared tax registration in the selected book. */
  Optional<DeclaredTaxRegistration> findTaxRegistration(TaxRegistrationId taxRegistrationId);
}
