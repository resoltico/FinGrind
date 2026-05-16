package dev.erst.fingrind.executor.bookkeeping.policy;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the built-in neutral bookkeeping policy pack contract. */
class CoreBookkeepingPolicyPackTest {
  @Test
  void current_returnsSingletonAndPublishedFacts() {
    CoreBookkeepingPolicyPack policyPack = CoreBookkeepingPolicyPack.current();

    assertSame(policyPack, CoreBookkeepingPolicyPack.current());
    assertEquals(ProtocolCatalog.accountingBaseline().defaultPolicyPack(), policyPack.facts());
    assertTrue(
        policyPack.facts().policyDimensions().stream()
            .anyMatch(dimension -> "accounting-basis".equals(dimension.dimensionId())));
  }

  @Test
  void accountingBasisPolicy_supportsCashAndAccrualOnly() {
    AccountingBasisPolicy policy = CoreBookkeepingPolicyPack.current().accountingBasisPolicy();

    assertEquals(List.of(AccountingBasis.CASH, AccountingBasis.ACCRUAL), policy.supportedBases());
    assertTrue(policy.supports(AccountingBasis.CASH));
    assertTrue(policy.supports(AccountingBasis.ACCRUAL));
    assertFalse(policy.supports(AccountingBasis.HYBRID_POLICY_DEFINED));
    assertFalse(policy.supports(AccountingBasis.EXTENSION_DEFINED));
  }

  @Test
  void closePolicy_closesTemporaryAccountsOnly() {
    ClosePolicy policy = CoreBookkeepingPolicyPack.current().closePolicy();

    assertEquals(
        FinancialPositionLineClassification.RETAINED_EARNINGS,
        policy.closingEquityLineClassification(bookIdentity(EntityForm.COMPANY)));
    assertEquals(
        FinancialPositionLineClassification.OWNER_CAPITAL,
        policy.closingEquityLineClassification(bookIdentity(EntityForm.FREELANCER)));
    assertEquals(
        FinancialPositionLineClassification.OWNER_CAPITAL,
        policy.closingEquityLineClassification(bookIdentity(EntityForm.SOLE_PROPRIETORSHIP)));
    assertEquals(
        FinancialPositionLineClassification.PARTNER_CURRENT,
        policy.closingEquityLineClassification(bookIdentity(EntityForm.PARTNERSHIP)));
    assertEquals(
        FinancialPositionLineClassification.ACCUMULATED_SURPLUS,
        policy.closingEquityLineClassification(bookIdentity(EntityForm.NONPROFIT)));
    assertEquals(
        FinancialPositionLineClassification.OTHER_EQUITY,
        policy.closingEquityLineClassification(bookIdentity(EntityForm.OTHER)));
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
        new DerivedEquityLine(
            "current-period-result",
            "Current Period Result",
            FinancialPositionLineClassification.CURRENT_PERIOD_RESULT),
        policyPack.statementPresentationPolicy().currentPeriodResultLine(bookIdentity()));
    assertFalse(policyPack.taxPolicy().supportsFirstClassTax());
    assertFalse(policyPack.foreignExchangePolicy().supportsTransactionCurrencies());
    assertFalse(policyPack.evidencePolicy().requiresFirstClassEvidence());
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
            return new DerivedEquityLine(
                "current-period-result",
                "Current Period Result",
                FinancialPositionLineClassification.CURRENT_PERIOD_RESULT);
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
                () ->
                    new DerivedEquityLine(
                        "  ",
                        "Current Period Result",
                        FinancialPositionLineClassification.CURRENT_PERIOD_RESULT))
            .getMessage());
    assertEquals(
        "lineName must not be blank.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new DerivedEquityLine(
                        "current-period-result",
                        "  ",
                        FinancialPositionLineClassification.CURRENT_PERIOD_RESULT))
            .getMessage());
    assertEquals(
        "Derived equity lines must use one equity financialPositionLineClassification.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new DerivedEquityLine(
                        "current-period-result",
                        "Current Period Result",
                        FinancialPositionLineClassification.CURRENT_ASSET))
            .getMessage());
  }
}
