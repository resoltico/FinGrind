package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.ComparativeRangeResolver;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Shared query helpers for bookkeeping read services. */
final class BookkeepingReadQuerySupport {
  private BookkeepingReadQuerySupport() {}

  static Optional<BookkeepingQueryRejection> accountRejection(
      BookkeepingReadStore bookStore, Optional<AccountCode> accountCode) {
    Objects.requireNonNull(bookStore, "bookStore");
    Objects.requireNonNull(accountCode, "accountCode");
    if (accountCode.isPresent() && bookStore.findAccount(accountCode.orElseThrow()).isEmpty()) {
      return Optional.of(new BookkeepingQueryRejection.UnknownAccount(accountCode.orElseThrow()));
    }
    return Optional.empty();
  }

  static TrialBalanceView trialBalanceView(
      BookkeepingReadStore bookStore, TrialBalanceCriteria query) {
    Objects.requireNonNull(bookStore, "bookStore");
    Objects.requireNonNull(query, "query");
    TrialBalanceView currentView = bookStore.trialBalance(query);
    var accountingRules = KernelAccountingRulesResolver.forBookIdentity(currentView.bookIdentity());
    var comparativeRange =
        ComparativeRangeResolver.asOf(
            currentView.bookIdentity(),
            currentView.resolvedEffectiveDateAsOf(),
            query.comparativeSelection(),
            accountingRules.statementComparativePolicy());
    return new TrialBalanceView(
        currentView.bookIdentity(),
        currentView.effectiveDateAsOf(),
        currentView.resolvedEffectiveDateAsOf(),
        comparativeRange,
        currentView.postingCoverage(),
        currentView.rows(),
        comparativeRange.effectiveDateTo().isPresent()
            ? bookStore
                .trialBalance(
                    new TrialBalanceCriteria(
                        comparativeRange.effectiveDateTo(),
                        query.postingCoverage(),
                        dev.erst.fingrind.core.ComparativeSelection.none()))
                .rows()
            : List.of());
  }
}
