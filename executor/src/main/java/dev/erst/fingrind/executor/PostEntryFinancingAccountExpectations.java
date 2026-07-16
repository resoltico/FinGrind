package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;

/** Role-account expectations owned by financing and payroll liabilities. */
final class PostEntryFinancingAccountExpectations {
  private PostEntryFinancingAccountExpectations() {}

  static PostEntryAccountExpectation currentLiability(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode,
        field,
        AccountType.LIABILITY,
        FinancialPositionLineClassification.CURRENT_LIABILITY,
        null,
        null);
  }

  static PostEntryAccountExpectation liability(AccountCode accountCode, String field) {
    return new PostEntryAccountExpectation(
        accountCode, field, AccountType.LIABILITY, null, null, null);
  }
}
