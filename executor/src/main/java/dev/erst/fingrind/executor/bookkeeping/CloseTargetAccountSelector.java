package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Selects one active declared close-target account for a required classification. */
public final class CloseTargetAccountSelector {
  private CloseTargetAccountSelector() {}

  /** Resolves the only active declared equity account for the required classification. */
  public static CloseTargetSelection select(
      FinancialPositionLineClassification requiredClassification,
      List<RegisteredAccount> accounts) {
    Objects.requireNonNull(requiredClassification, "requiredClassification");
    Objects.requireNonNull(accounts, "accounts");
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
      return new RejectedCloseTargetSelection(
          new CloseTargetAccountCandidateMissing(
              requiredClassification,
              matchingCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    if (activeCandidates.size() > 1) {
      return new RejectedCloseTargetSelection(
          new CloseTargetAccountCandidateAmbiguous(
              requiredClassification,
              activeCandidates.stream().map(RegisteredAccount::accountCode).toList()));
    }
    return new AcceptedCloseTargetSelection(activeCandidates.getFirst());
  }
}
