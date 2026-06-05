package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlanner;
import dev.erst.fingrind.executor.bookkeeping.ResultHoldingSelection;
import dev.erst.fingrind.executor.bookkeeping.policy.KernelAccountingRulesResolver;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Objects;

/** Resolves result-transfer read policy for one initialized bookkeeping store. */
public final class BookkeepingResultTransferReadSupport {
  private BookkeepingResultTransferReadSupport() {}

  /** Returns the current result-holding selection for one initialized book. */
  public static ResultHoldingSelection resultHoldingSelection(
      BookIdentity bookIdentity, BookkeepingReadStore bookStore) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(bookStore, "bookStore");
    return new PeriodResultTransferPlanner(
            KernelAccountingRulesResolver.forBookIdentity(bookIdentity).resultTransferPolicy())
        .resultHoldingAccount(bookIdentity, bookStore.allAccounts());
  }

  /** Returns the required result-holding classification for one initialized book. */
  public static FinancialPositionLineClassification requiredResultHoldingClassification(
      BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return KernelAccountingRulesResolver.forBookIdentity(bookIdentity)
        .resultTransferPolicy()
        .resultHoldingLineClassification(bookIdentity);
  }
}
