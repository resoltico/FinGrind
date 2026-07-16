package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;

/** Role-account expectations owned by owner-equity events. */
final class PostEntryEquityAccountExpectations {
  private PostEntryEquityAccountExpectations() {}

  static PostEntryAccountExpectation equityContribution(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.EQUITY,
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
        null,
        null);
  }

  static PostEntryAccountExpectation equityWithdrawal(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.EQUITY,
        FinancialPositionLineClassification.EQUITY_WITHDRAWAL,
        null,
        null);
  }
}
