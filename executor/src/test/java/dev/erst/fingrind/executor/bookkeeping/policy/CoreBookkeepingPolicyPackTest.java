package dev.erst.fingrind.executor.bookkeeping.policy;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingPolicyProfile;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import org.junit.jupiter.api.Test;

/** Covers the built-in neutral bookkeeping policy pack contract. */
class CoreBookkeepingPolicyPackTest {
  @Test
  void current_returnsSingleton() {
    CoreBookkeepingPolicyPack policyPack = CoreBookkeepingPolicyPack.current();

    assertSame(policyPack, CoreBookkeepingPolicyPack.current());
  }

  @Test
  void profileSelection_resolvesTheCurrentBuiltInPolicyPack() {
    CoreBookkeepingPolicyPack policyPack = CoreBookkeepingPolicyPack.current();

    assertEquals(
        AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1, policyPack.profile());
    assertSame(
        policyPack,
        BuiltInBookkeepingPolicyPacks.forProfile(
            AccountingPolicyProfile.INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1));
  }

  @Test
  void identitySelection_resolvesTheCurrentBuiltInPolicyPack() {
    assertSame(
        CoreBookkeepingPolicyPack.current(),
        BuiltInBookkeepingPolicyPacks.forBookIdentity(bookIdentity()));
  }

  @Test
  void closePolicy_closesTemporaryAccountsOnly() {
    ClosePolicy policy = CoreBookkeepingPolicyPack.current().closePolicy();

    assertEquals(
        FinancialPositionLineClassification.ACCUMULATED_RESULT,
        policy.closingEquityLineClassification(bookIdentity()));
    assertFalse(policy.closesAccountType(AccountType.ASSET));
    assertFalse(policy.closesAccountType(AccountType.LIABILITY));
    assertFalse(policy.closesAccountType(AccountType.EQUITY));
    assertTrue(policy.closesAccountType(AccountType.REVENUE));
    assertTrue(policy.closesAccountType(AccountType.EXPENSE));
  }

  @Test
  void neutralKernelPolicies_publishCurrentCapabilityLimits() {
    CoreBookkeepingPolicyPack policyPack = CoreBookkeepingPolicyPack.current();

    assertTrue(policyPack.chartPolicy().supportsHierarchicalChart());
    assertTrue(policyPack.statementPresentationPolicy().supportsRichClassification());
    assertEquals(
        new DerivedEquityLine("current-period-result", "Current Period Result"),
        policyPack.statementPresentationPolicy().currentPeriodResultLine(bookIdentity()));
    assertEquals(
        FiscalYearAnchoredStatementComparativePolicy.class,
        policyPack.statementComparativePolicy().getClass());
  }

  @Test
  void statementPresentationPolicyHelpers_rejectNullAndInvalidDerivedEquityLines() {
    StatementPresentationPolicy policy =
        new StatementPresentationPolicy() {
          @Override
          public boolean supportsRichClassification() {
            return true;
          }

          @Override
          public DerivedEquityLine currentPeriodResultLine(
              dev.erst.fingrind.core.BookIdentity bookIdentity) {
            return new DerivedEquityLine("current-period-result", "Current Period Result");
          }
        };

    assertSame(policy, StatementPresentationPolicy.requirePolicy(policy));
    assertEquals(
        "statementPresentationPolicy",
        assertThrows(
                NullPointerException.class,
                () ->
                    StatementPresentationPolicy.requirePolicy(
                        nullOf(StatementPresentationPolicy.class)))
            .getMessage());
    assertEquals(
        "lineCode must not be blank.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new DerivedEquityLine("  ", "Current Period Result"))
            .getMessage());
    assertEquals(
        "lineName must not be blank.",
        assertThrows(
                IllegalArgumentException.class,
                () -> new DerivedEquityLine("current-period-result", "  "))
            .getMessage());
  }
}
