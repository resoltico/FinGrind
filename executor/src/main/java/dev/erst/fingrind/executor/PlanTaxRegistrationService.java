package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxDefinitionViolation;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.PlanTaxRegistrationMutationOutcome;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PlanTaxRegistrationStore;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/** Application service for the sole tax-registration mutation family admitted as a plan child. */
public final class PlanTaxRegistrationService {
  private final BookLifecycleReader lifecycleReader;
  private final AccountLookupStore accountLookupStore;
  private final PlanTaxRegistrationStore planStore;
  private final Clock clock;

  /** Creates the capability-confined tax-registration service for aggregate ledger plans. */
  public PlanTaxRegistrationService(
      BookLifecycleReader lifecycleReader,
      AccountLookupStore accountLookupStore,
      PlanTaxRegistrationStore planStore,
      Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.accountLookupStore = Objects.requireNonNull(accountLookupStore, "accountLookupStore");
    this.planStore = Objects.requireNonNull(planStore, "planStore");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Declares or updates one tax registration as a child of the active aggregate plan. */
  public PlanTaxRegistrationMutationOutcome declareTaxRegistration(
      DeclareTaxRegistrationCommand command,
      AttestationPlanOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return new PlanTaxRegistrationMutationOutcome.Rejected(
          new TaxDeclarationRejection.BookNotInitialized());
    }
    List<TaxDefinitionViolation> violations =
        TaxValidationSupport.declarationViolations(command, accountLookupStore);
    if (!violations.isEmpty()) {
      return new PlanTaxRegistrationMutationOutcome.Rejected(
          new TaxDeclarationRejection.DefinitionViolations(violations));
    }
    return planStore.declareTaxRegistrationForPlan(command, clock.instant(), attestationAuthorizer);
  }
}
