package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Application service that owns explicit tax-registration declaration and update commands. */
public final class TaxAdministrationService {
  private final BookLifecycleReader lifecycleReader;
  private final AccountLookupStore accountLookupStore;
  private final TaxAdministrationStore taxAdministrationStore;
  private final Clock clock;

  /** Creates the tax-administration service with its application-owned seams. */
  public TaxAdministrationService(
      BookLifecycleReader lifecycleReader,
      AccountLookupStore accountLookupStore,
      TaxAdministrationStore taxAdministrationStore,
      Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.accountLookupStore = Objects.requireNonNull(accountLookupStore, "accountLookupStore");
    this.taxAdministrationStore =
        Objects.requireNonNull(taxAdministrationStore, "taxAdministrationStore");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Declares or updates one owned tax registration in the selected book. */
  public DeclareTaxRegistrationResult declareTaxRegistration(
      DeclareTaxRegistrationCommand command, AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return new DeclareTaxRegistrationResult.Rejected(
          new TaxDeclarationRejection.BookNotInitialized());
    }
    List<TaxDefinitionViolation> violations =
        TaxValidationSupport.declarationViolations(command, accountLookupStore);
    if (!violations.isEmpty()) {
      return new DeclareTaxRegistrationResult.Rejected(
          new TaxDeclarationRejection.DefinitionViolations(violations));
    }
    return taxAdministrationStore.declareTaxRegistration(
        command, clock.instant(), attestationAuthorizer);
  }
}
