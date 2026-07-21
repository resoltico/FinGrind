package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import java.time.Instant;

/** Writes declared tax-registration mutations. */
@FunctionalInterface
public interface TaxAdministrationStore {
  /** Declares or updates one tax registration in the selected book. */
  DeclareTaxRegistrationResult declareTaxRegistration(
      DeclareTaxRegistrationCommand command,
      Instant declaredAt,
      AttestationOperationAuthorizer attestationAuthorizer);
}
