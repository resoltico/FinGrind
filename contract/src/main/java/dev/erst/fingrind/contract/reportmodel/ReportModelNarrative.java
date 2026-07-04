package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared narrative helpers for report ranges, summaries, and ledger descriptions. */
final class ReportModelNarrative {
  private ReportModelNarrative() {}

  static String counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    List<String> counterparts =
        postingFact.journalEntry().lines().stream()
            .map(line -> line.accountCode().value())
            .filter(accountCode -> !accountCode.equals(account.accountCode().value()))
            .distinct()
            .toList();
    return counterparts.isEmpty() ? "(self)" : String.join(", ", counterparts);
  }

  static String runningBalance(AccountLedgerEntry entry) {
    return ReportModelDisplay.displayMoney(entry.runningNetAmount())
        + " "
        + ReportModelDisplay.displayBalanceSide(entry.runningBalanceSide());
  }

  static String accountLedgerEntrySummary(PostingFact postingFact) {
    String origin = ReportModelDisplay.displayPostingOriginKind(postingFact.postingOriginKind());
    return postingFact
        .reversalReference()
        .map(reference -> origin + " / Reversal posting of " + reference.priorPostingId().value())
        .orElse(origin + " / Direct posting");
  }

  static boolean hasMeaningfulBalances(List<CurrencyBalance> balances) {
    return balances.stream()
        .anyMatch(balance -> !balance.debitTotal().isZero() || !balance.creditTotal().isZero());
  }

  static String joinedBalancesText(List<CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "Zero across all currencies.";
    }
    return balances.stream()
        .map(
            balance ->
                ReportModelDisplay.displayMoney(balance.debitTotal())
                    + " debit, "
                    + ReportModelDisplay.displayMoney(balance.creditTotal())
                    + " credit, "
                    + ReportModelDisplay.displayMoney(balance.netAmount())
                    + " "
                    + ReportModelDisplay.displayBalanceSide(balance.balanceSide()))
        .reduce((left, right) -> left + ", " + right)
        .orElse("Zero across all currencies.");
  }

  static String noMatches(String subjectPlural) {
    return "No " + subjectPlural + " matched the selected scope.";
  }

  static String dateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    String lower = effectiveDateFrom == null ? "book start" : effectiveDateFrom.toString();
    String upper =
        effectiveDateTo == null
            ? "latest effective date in the selected book"
            : effectiveDateTo.toString();
    return lower + " to " + upper;
  }

  static String comparativeRange(EffectiveDateRange effectiveDateRange) {
    String lower =
        effectiveDateRange.effectiveDateFrom().map(LocalDate::toString).orElse("book start");
    String upper =
        effectiveDateRange
            .effectiveDateTo()
            .map(LocalDate::toString)
            .orElse("current book horizon");
    return lower + " to " + upper;
  }
}
