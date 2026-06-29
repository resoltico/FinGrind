package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Objects;

/** Resolves result-holding close-read helpers for one initialized bookkeeping store. */
public final class BookkeepingResultHoldingReadSupport {
  private BookkeepingResultHoldingReadSupport() {}

  /** Returns the current result-holding selection for one initialized book. */
  public static InterimResultTargetSelection resultHoldingSelection(
      BookIdentity bookIdentity, BookkeepingReadStore bookStore) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(bookStore, "bookStore");
    return new InterimResultSweepPlanner(
            KernelAccountingRulesResolver.forBookIdentity(bookIdentity).closePostingPolicy())
        .resultHoldingAccount(bookIdentity, bookStore.allAccounts());
  }

  /** Returns the required result-holding classification for one initialized book. */
  public static FinancialPositionLineClassification requiredResultHoldingClassification(
      BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return KernelAccountingRulesResolver.forBookIdentity(bookIdentity)
        .closePostingPolicy()
        .resultHoldingLineClassification(bookIdentity);
  }
}
