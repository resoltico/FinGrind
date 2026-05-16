package dev.erst.fingrind.executor.bookkeeping.policy;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;

/** Operational seam for closeable account classes and close-target doctrine. */
public interface ClosePolicy {
  /** Returns whether the given account type participates in current-period close clearing. */
  boolean closesAccountType(AccountType accountType);

  /**
   * Returns the required financial-position classification for the selected closing-equity account.
   */
  FinancialPositionLineClassification closingEquityLineClassification(BookIdentity bookIdentity);
}
