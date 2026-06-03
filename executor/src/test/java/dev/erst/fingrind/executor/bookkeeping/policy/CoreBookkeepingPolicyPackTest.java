package dev.erst.fingrind.executor.bookkeeping.policy;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.AccountingFrameworkPosition;
import dev.erst.fingrind.core.AccountingKernelProfileId;
import dev.erst.fingrind.core.BookDoctrine;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import org.junit.jupiter.api.Test;

/** Covers the built-in neutral bookkeeping policy pack contract. */
class InternalManagementKernelAccountingRulesTest {
  @Test
  void current_returnsSingleton() {
    InternalManagementKernelAccountingRules accountingRules =
        InternalManagementKernelAccountingRules.current();

    assertSame(accountingRules, InternalManagementKernelAccountingRules.current());
  }

  @Test
  void resolver_returnsTheCurrentBuiltInPolicyPack() {
    InternalManagementKernelAccountingRules accountingRules =
        InternalManagementKernelAccountingRules.current();

    assertSame(accountingRules, KernelAccountingRulesResolver.forBookIdentity(bookIdentity()));
  }

  @Test
  void resolver_rejectsUnsupportedAccountingKernelProfiles() {
    BookIdentity unsupportedProfileBook =
        new BookIdentity(
            bookIdentity().entityProfile(),
            new BookDoctrine(
                new AccountingKernelProfileId("unsupported-kernel-profile"),
                AccountingBasis.CASH_BASIS,
                AccountingFrameworkPosition.NON_STATUTORY_INTERNAL_MANAGEMENT,
                EntityForm.OWNER_MANAGED_SINGLE_ENTITY,
                BookTemplateId.OWNER_MANAGED_SERVICE_CASH),
            bookIdentity().functionalCurrency(),
            bookIdentity().fiscalYearStart());

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> KernelAccountingRulesResolver.forBookIdentity(unsupportedProfileBook));

    assertEquals(
        "Unsupported accounting kernel profile: unsupported-kernel-profile.", failure.getMessage());
  }

  @Test
  void resultTransferPolicy_closesTemporaryAccountsOnly() {
    ResultTransferPolicy policy =
        InternalManagementKernelAccountingRules.current().resultTransferPolicy();

    assertEquals(
        FinancialPositionLineClassification.RESULT_HOLDING,
        policy.resultHoldingLineClassification(bookIdentity()));
    assertFalse(policy.closesAccountType(AccountType.ASSET));
    assertFalse(policy.closesAccountType(AccountType.LIABILITY));
    assertFalse(policy.closesAccountType(AccountType.EQUITY));
    assertTrue(policy.closesAccountType(AccountType.REVENUE));
    assertTrue(policy.closesAccountType(AccountType.EXPENSE));
  }

  @Test
  void neutralKernelPolicies_publishCurrentCapabilityLimits() {
    InternalManagementKernelAccountingRules accountingRules =
        InternalManagementKernelAccountingRules.current();

    assertTrue(accountingRules.chartPolicy().supportsHierarchicalChart());
    assertTrue(accountingRules.statementPresentationPolicy().supportsRichClassification());
    assertEquals(
        new DerivedEquityLine("current-period-result", "Current Period Result"),
        accountingRules.statementPresentationPolicy().currentPeriodResultLine(bookIdentity()));
    assertEquals(
        FiscalYearAnchoredStatementComparativePolicy.class,
        accountingRules.statementComparativePolicy().getClass());
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
