package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.policy.ClosePostingPolicy;
import java.util.List;
import java.util.Objects;

/** Selects the single active result-holding account required by one close-posting policy. */
final class InterimResultSweepHoldingAccountSelector {
  private final ClosePostingPolicy closePostingPolicy;

  InterimResultSweepHoldingAccountSelector(ClosePostingPolicy closePostingPolicy) {
    this.closePostingPolicy = Objects.requireNonNull(closePostingPolicy, "closePostingPolicy");
  }

  InterimResultTargetSelection resultHoldingAccount(
      BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(accounts, "accounts");
    var requiredClassification = closePostingPolicy.resultHoldingLineClassification(bookIdentity);
    CloseTargetSelection selection =
        CloseTargetAccountSelector.select(requiredClassification, accounts);
    if (selection instanceof AcceptedCloseTargetSelection accepted) {
      return new AcceptedInterimResultTargetSelection(accepted.account());
    }
    RejectedCloseTargetSelection rejected = (RejectedCloseTargetSelection) selection;
    return rejected.rejection()
            instanceof BookkeepingAdministrationRejection.CloseTargetAccountCandidateMissing
        ? new RejectedInterimResultTargetSelection(
            (BookkeepingAdministrationRejection.CloseTargetAccountCandidateMissing)
                rejected.rejection())
        : new RejectedInterimResultTargetSelection(
            (CloseTargetAccountCandidateAmbiguous) rejected.rejection());
  }
}
