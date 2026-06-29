package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;

/** Operational seam for close-participating account classes and result-target doctrine. */
public interface ClosePostingPolicy {
  /** Returns whether the given account type participates in current-interim result sweep. */
  boolean closesAccountType(AccountType accountType);

  /**
   * Returns the required financial-position classification for the selected result-holding account.
   */
  FinancialPositionLineClassification resultHoldingLineClassification(BookIdentity bookIdentity);
}
