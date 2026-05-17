package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for chart hierarchy aggregate invariants. */
class ChartOfAccountsTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void ofRejectsDuplicateAccountCodes() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ChartOfAccounts.of(
                    List.of(
                        assetAccount("1000", Optional.empty(), true),
                        assetAccount("1000", Optional.empty(), true))));

    assertEquals(
        "Duplicate declared account code inside chart aggregate: AccountCode[value=1000]",
        exception.getMessage());
  }

  @Test
  void validateAcceptsDeclarationsWithoutParentAndCompatibleParentChains() {
    ChartOfAccounts chart =
        ChartOfAccounts.of(
            List.of(
                assetAccount("1000", Optional.empty(), true),
                assetAccount("1010", Optional.of(new AccountCode("1000")), true)));

    assertEquals(
        Optional.empty(),
        chart.validate(
            new AccountDeclaration(
                new AccountCode("1020"),
                new AccountName("Petty Cash"),
                AccountType.ASSET,
                accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("1010")),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty()))));
    assertEquals(
        Optional.empty(),
        chart.validate(
            new AccountDeclaration(
                new AccountCode("4000"),
                new AccountName("Revenue"),
                AccountType.REVENUE,
                accountRole(AccountType.REVENUE, NormalBalance.CREDIT),
                accountTaxonomy(AccountType.REVENUE))));

    ChartOfAccounts chartWithMissingAncestor =
        ChartOfAccounts.of(
            List.of(assetAccount("1010", Optional.of(new AccountCode("9999")), true)));
    assertEquals(
        Optional.empty(),
        chartWithMissingAncestor.validate(
            new AccountDeclaration(
                new AccountCode("1020"),
                new AccountName("Child Cash"),
                AccountType.ASSET,
                accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("1010")),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty()))));
  }

  @Test
  void validateRejectsMissingInactiveAndTypeConflictingParents() {
    ChartOfAccounts missingParentChart =
        ChartOfAccounts.of(List.of(assetAccount("1000", Optional.empty(), true)));
    Optional<BookkeepingAdministrationRejection> missingParent =
        missingParentChart.validate(
            new AccountDeclaration(
                new AccountCode("1010"),
                new AccountName("Child Cash"),
                AccountType.ASSET,
                accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("9999")),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty())));

    ChartOfAccounts inactiveParentChart =
        ChartOfAccounts.of(List.of(assetAccount("1000", Optional.empty(), false)));
    Optional<BookkeepingAdministrationRejection> inactiveParent =
        inactiveParentChart.validate(
            new AccountDeclaration(
                new AccountCode("1010"),
                new AccountName("Child Cash"),
                AccountType.ASSET,
                accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("1000")),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty())));

    ChartOfAccounts typeConflictChart =
        ChartOfAccounts.of(
            List.of(
                registeredAccount(
                    new AccountCode("2000"),
                    new AccountName("Trade Payables"),
                    AccountType.LIABILITY,
                    accountRole(AccountType.LIABILITY, NormalBalance.CREDIT),
                    financialPositionTaxonomy(
                        FinancialPositionLineClassification.CURRENT_LIABILITY),
                    true,
                    DECLARED_AT)));
    Optional<BookkeepingAdministrationRejection> typeConflict =
        typeConflictChart.validate(
            new AccountDeclaration(
                new AccountCode("1010"),
                new AccountName("Child Cash"),
                AccountType.ASSET,
                accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("2000")),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty())));

    assertEquals(
        "9999",
        assertInstanceOf(
                BookkeepingAdministrationRejection.ParentAccountMissing.class,
                missingParent.orElseThrow())
            .parentAccountCode()
            .value());
    assertEquals(
        "1000",
        assertInstanceOf(
                BookkeepingAdministrationRejection.ParentAccountInactive.class,
                inactiveParent.orElseThrow())
            .parentAccountCode()
            .value());
    assertEquals(
        AccountType.LIABILITY,
        assertInstanceOf(
                BookkeepingAdministrationRejection.ParentAccountTypeConflict.class,
                typeConflict.orElseThrow())
            .parentAccountType());
  }

  @Test
  void validateRejectsDirectAndTransitiveCycles() {
    ChartOfAccounts directCycleChart =
        ChartOfAccounts.of(List.of(assetAccount("1000", Optional.empty(), true)));
    Optional<BookkeepingAdministrationRejection> directCycle =
        directCycleChart.validate(
            new AccountDeclaration(
                new AccountCode("1000"),
                new AccountName("Self Parent"),
                AccountType.ASSET,
                accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("1000")),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty())));

    ChartOfAccounts transitiveCycleChart =
        ChartOfAccounts.of(
            List.of(
                assetAccount("1000", Optional.of(new AccountCode("1020")), true),
                assetAccount("1010", Optional.of(new AccountCode("1000")), true),
                assetAccount("1020", Optional.empty(), true)));
    Optional<BookkeepingAdministrationRejection> transitiveCycle =
        transitiveCycleChart.validate(
            new AccountDeclaration(
                new AccountCode("1020"),
                new AccountName("Cycle Root"),
                AccountType.ASSET,
                accountRole(AccountType.ASSET, NormalBalance.DEBIT),
                new AccountTaxonomy(
                    Optional.of(new AccountCode("1010")),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty())));

    assertEquals(
        "1000",
        assertInstanceOf(
                BookkeepingAdministrationRejection.AccountHierarchyCycle.class,
                directCycle.orElseThrow())
            .accountCode()
            .value());
    assertEquals(
        "1010",
        assertInstanceOf(
                BookkeepingAdministrationRejection.AccountHierarchyCycle.class,
                transitiveCycle.orElseThrow())
            .parentAccountCode()
            .value());
  }

  private static RegisteredAccount assetAccount(
      String accountCode, Optional<AccountCode> parentAccountCode, boolean active) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName("Account " + accountCode),
        AccountType.ASSET,
        accountRole(AccountType.ASSET, NormalBalance.DEBIT),
        new AccountTaxonomy(
            parentAccountCode,
            Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
            Optional.empty()),
        active,
        DECLARED_AT);
  }
}
