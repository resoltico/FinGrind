package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.policy.ResultTransferPolicy;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Selects the single active result-holding account required by one result-transfer policy. */
final class PeriodResultTransferHoldingAccountSelector {
  private final ResultTransferPolicy resultTransferPolicy;

  PeriodResultTransferHoldingAccountSelector(ResultTransferPolicy resultTransferPolicy) {
    this.resultTransferPolicy =
        Objects.requireNonNull(resultTransferPolicy, "resultTransferPolicy");
  }

  ResultHoldingSelection resultHoldingAccount(
      BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(accounts, "accounts");
    var requiredClassification = resultTransferPolicy.resultHoldingLineClassification(bookIdentity);
    List<RegisteredAccount> matchingCandidates =
        accounts.stream()
            .filter(account -> account.accountType() == AccountType.EQUITY)
            .filter(
                account ->
                    account
                        .accountTaxonomy()
                        .financialPositionLineClassification()
                        .filter(requiredClassification::equals)
                        .isPresent())
            .sorted(Comparator.comparing(account -> account.accountCode().value()))
            .toList();
    List<RegisteredAccount> activeCandidates =
        matchingCandidates.stream().filter(RegisteredAccount::active).toList();
    if (activeCandidates.isEmpty()) {
      return new RejectedResultHoldingSelection(
          new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateMissing(
              requiredClassification,
              matchingCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    if (activeCandidates.size() > 1) {
      return new RejectedResultHoldingSelection(
          new BookkeepingAdministrationRejection.ResultHoldingAccountCandidateAmbiguous(
              requiredClassification,
              activeCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    return new AcceptedResultHoldingSelection(activeCandidates.getFirst());
  }
}
