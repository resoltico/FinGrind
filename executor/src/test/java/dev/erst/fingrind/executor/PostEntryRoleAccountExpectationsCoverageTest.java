package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import org.junit.jupiter.api.Test;

/** Direct coverage for standalone account-expectation helpers. */
class PostEntryRoleAccountExpectationsCoverageTest {
  @Test
  void nonCashAsset_marksAssetExpectationsWithNonCashClassification() {
    PostEntryRoleAccountExpectations.AccountExpectation expectation =
        PostEntryRoleAccountExpectations.nonCashAsset(
            new AccountCode("1300"), "settlementAdjunct.accountCode");

    assertEquals(AccountType.ASSET, expectation.expectedAccountType());
    assertEquals(
        CashFlowAssetClassification.NON_CASH, expectation.expectedCashFlowAssetClassification());
  }
}
