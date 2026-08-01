package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.ChartOfAccounts;
import dev.erst.fingrind.executor.bookkeeping.PlanAccountDeclarationOutcome;
import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PlanAccountDeclarationStore;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/** Application service for the sole account mutation family admitted as a ledger-plan child. */
public final class PlanAccountDeclarationService {
  private final BookLifecycleReader lifecycleReader;
  private final AccountCatalogStore accountCatalogStore;
  private final PlanAccountDeclarationStore planStore;
  private final Clock clock;

  /** Creates the capability-confined account declaration service for aggregate ledger plans. */
  public PlanAccountDeclarationService(
      BookLifecycleReader lifecycleReader,
      AccountCatalogStore accountCatalogStore,
      PlanAccountDeclarationStore planStore,
      Clock clock) {
    this.lifecycleReader = Objects.requireNonNull(lifecycleReader, "lifecycleReader");
    this.accountCatalogStore = Objects.requireNonNull(accountCatalogStore, "accountCatalogStore");
    this.planStore = Objects.requireNonNull(planStore, "planStore");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /** Declares or reactivates one account as a child of the currently active aggregate plan. */
  public PlanAccountDeclarationOutcome declareAccount(
      AccountDeclaration command, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    if (!lifecycleReader.allowsInitializedWorkflow()) {
      return new PlanAccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    Optional<BookkeepingAdministrationRejection> rejection =
        ChartOfAccounts.of(accountCatalogStore.allAccounts()).validate(command);
    if (rejection.isPresent()) {
      return new PlanAccountDeclarationOutcome.Rejected(rejection.orElseThrow());
    }
    return planStore.declareAccountForPlan(command, clock.instant(), attestationAuthorizer);
  }
}
