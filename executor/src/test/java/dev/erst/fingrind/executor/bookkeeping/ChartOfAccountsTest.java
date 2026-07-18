package dev.erst.fingrind.executor.bookkeeping;

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
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.ContraAccountRelationshipViolation;
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
                assetHeaderAccount("1000", Optional.empty(), true),
                assetHeaderAccount("1010", Optional.of(new AccountCode("1000")), true)));

    assertEquals(
        Optional.empty(),
        chart.validate(
            new AccountDeclaration(
                new AccountCode("1020"),
                new AccountName("Petty Cash"),
                AccountType.ASSET,
                currentAssetTaxonomy(Optional.of(new AccountCode("1010"))))));
    assertEquals(
        Optional.empty(),
        chart.validate(
            new AccountDeclaration(
                new AccountCode("4000"),
                new AccountName("Revenue"),
                AccountType.REVENUE,
                accountTaxonomy(AccountType.REVENUE, NormalBalance.CREDIT))));

    ChartOfAccounts chartWithMissingAncestor =
        ChartOfAccounts.of(
            List.of(assetHeaderAccount("1010", Optional.of(new AccountCode("9999")), true)));
    assertEquals(
        Optional.empty(),
        chartWithMissingAncestor.validate(
            new AccountDeclaration(
                new AccountCode("1020"),
                new AccountName("Child Cash"),
                AccountType.ASSET,
                currentAssetTaxonomy(Optional.of(new AccountCode("1010"))))));
  }

  @Test
  void validateConstrainsContraRelationshipsToActiveCompatiblePostableNonContraTargets() {
    assertContraViolation(
        ChartOfAccounts.of(List.of()),
        contraDeclaration("1000", "1000", AccountType.ASSET, currentAssetContraTaxonomy("1000")),
        ContraAccountRelationshipViolation.SELF_REFERENCE);
    assertContraViolation(
        ChartOfAccounts.of(List.of()),
        contraDeclaration("1090", "1010", AccountType.ASSET, currentAssetContraTaxonomy("1010")),
        ContraAccountRelationshipViolation.TARGET_MISSING);
    assertContraViolation(
        ChartOfAccounts.of(List.of(assetAccount("1010", Optional.empty(), false))),
        contraDeclaration("1090", "1010", AccountType.ASSET, currentAssetContraTaxonomy("1010")),
        ContraAccountRelationshipViolation.TARGET_INACTIVE);
    assertContraViolation(
        ChartOfAccounts.of(List.of(assetHeaderAccount("1010", Optional.empty(), true))),
        contraDeclaration("1090", "1010", AccountType.ASSET, currentAssetContraTaxonomy("1010")),
        ContraAccountRelationshipViolation.TARGET_NOT_POSTABLE);
    assertContraViolation(
        ChartOfAccounts.of(
            List.of(
                registeredAccount(
                    new AccountCode("1010"),
                    new AccountName("Existing Contra Asset"),
                    AccountType.ASSET,
                    currentAssetContraTaxonomy("1000"),
                    true,
                    DECLARED_AT))),
        contraDeclaration("1090", "1010", AccountType.ASSET, currentAssetContraTaxonomy("1010")),
        ContraAccountRelationshipViolation.TARGET_IS_CONTRA);
    assertContraViolation(
        ChartOfAccounts.of(
            List.of(
                registeredAccount(
                    new AccountCode("2010"),
                    new AccountName("Trade Payables"),
                    AccountType.LIABILITY,
                    financialPositionTaxonomy(
                        FinancialPositionLineClassification.CURRENT_LIABILITY),
                    true,
                    DECLARED_AT))),
        contraDeclaration("1090", "2010", AccountType.ASSET, currentAssetContraTaxonomy("2010")),
        ContraAccountRelationshipViolation.ACCOUNT_TYPE_MISMATCH);
    assertContraViolation(
        ChartOfAccounts.of(List.of(assetAccount("1010", Optional.empty(), true))),
        contraDeclaration("1090", "1010", AccountType.ASSET, nonCurrentAssetContraTaxonomy("1010")),
        ContraAccountRelationshipViolation.STATEMENT_TAXONOMY_MISMATCH);

    assertEquals(
        Optional.empty(),
        ChartOfAccounts.of(List.of(assetAccount("1010", Optional.empty(), true)))
            .validate(
                contraDeclaration(
                    "1090", "1010", AccountType.ASSET, currentAssetContraTaxonomy("1010"))));
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
                currentAssetTaxonomy(Optional.of(new AccountCode("9999")))));

    ChartOfAccounts inactiveParentChart =
        ChartOfAccounts.of(List.of(assetAccount("1000", Optional.empty(), false)));
    Optional<BookkeepingAdministrationRejection> inactiveParent =
        inactiveParentChart.validate(
            new AccountDeclaration(
                new AccountCode("1010"),
                new AccountName("Child Cash"),
                AccountType.ASSET,
                currentAssetTaxonomy(Optional.of(new AccountCode("1000")))));

    ChartOfAccounts typeConflictChart =
        ChartOfAccounts.of(
            List.of(
                registeredAccount(
                    new AccountCode("2000"),
                    new AccountName("Trade Payables"),
                    AccountType.LIABILITY,
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
                currentAssetTaxonomy(Optional.of(new AccountCode("2000")))));

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
  void validateRejectsNonHeaderAndTaxonomyConflictingParents() {
    ChartOfAccounts nonHeaderParentChart =
        ChartOfAccounts.of(List.of(assetAccount("1000", Optional.empty(), true)));
    Optional<BookkeepingAdministrationRejection> nonHeaderParent =
        nonHeaderParentChart.validate(
            new AccountDeclaration(
                new AccountCode("1010"),
                new AccountName("Child Cash"),
                AccountType.ASSET,
                currentAssetTaxonomy(Optional.of(new AccountCode("1000")))));

    ChartOfAccounts taxonomyConflictChart =
        ChartOfAccounts.of(
            List.of(
                registeredAccount(
                    new AccountCode("1100"),
                    new AccountName("Cash Header"),
                    AccountType.ASSET,
                    currentAssetHeaderTaxonomy(Optional.empty()),
                    true,
                    DECLARED_AT)));
    Optional<BookkeepingAdministrationRejection> taxonomyConflict =
        taxonomyConflictChart.validate(
            new AccountDeclaration(
                new AccountCode("1110"),
                new AccountName("Equipment"),
                AccountType.ASSET,
                nonCurrentAssetTaxonomy(Optional.of(new AccountCode("1100")))));

    assertEquals(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        assertInstanceOf(
                BookkeepingAdministrationRejection.ParentAccountNotHeader.class,
                nonHeaderParent.orElseThrow())
            .parentAccountNodeKind());
    assertEquals(
        FinancialPositionLineClassification.CURRENT_ASSET,
        assertInstanceOf(
                BookkeepingAdministrationRejection.ParentAccountTaxonomyConflict.class,
                taxonomyConflict.orElseThrow())
            .parentAccountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow());
  }

  @Test
  void validateAcceptsMatchingParentAndChildClassificationsWithoutSeparateRoleChecks() {
    ChartOfAccounts chart =
        ChartOfAccounts.of(
            List.of(
                registeredAccount(
                    new AccountCode("1100"),
                    new AccountName("Contra Asset Header"),
                    AccountType.ASSET,
                    currentAssetHeaderTaxonomy(Optional.empty()),
                    true,
                    DECLARED_AT)));

    Optional<BookkeepingAdministrationRejection> rejection =
        chart.validate(
            new AccountDeclaration(
                new AccountCode("1110"),
                new AccountName("Cash Child"),
                AccountType.ASSET,
                currentAssetTaxonomy(Optional.of(new AccountCode("1100")))));

    assertEquals(Optional.empty(), rejection);
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
                currentAssetTaxonomy(Optional.of(new AccountCode("1000")))));

    ChartOfAccounts transitiveCycleChart =
        ChartOfAccounts.of(
            List.of(
                assetHeaderAccount("1000", Optional.of(new AccountCode("1020")), true),
                assetHeaderAccount("1010", Optional.of(new AccountCode("1000")), true),
                assetHeaderAccount("1020", Optional.empty(), true)));
    Optional<BookkeepingAdministrationRejection> transitiveCycle =
        transitiveCycleChart.validate(
            new AccountDeclaration(
                new AccountCode("1020"),
                new AccountName("Cycle Root"),
                AccountType.ASSET,
                currentAssetTaxonomy(Optional.of(new AccountCode("1010")))));

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

  @Test
  void validateAllowsResultHoldingDeclarationWhenNoActiveCandidateExists() {
    ChartOfAccounts chart =
        ChartOfAccounts.of(
            List.of(
                resultHoldingAccount("3200", false), assetAccount("1000", Optional.empty(), true)));

    assertEquals(
        Optional.empty(),
        chart.validate(
            new AccountDeclaration(
                new AccountCode("3210"),
                new AccountName("Replacement Result Holding"),
                AccountType.EQUITY,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                    Optional.empty(),
                    Optional.empty()))));
  }

  @Test
  void validateRejectsAmbiguousActiveResultHoldingDeclarations() {
    ChartOfAccounts chart = ChartOfAccounts.of(List.of(resultHoldingAccount("3200", true)));

    CloseTargetAccountCandidateAmbiguous rejection =
        assertInstanceOf(
            CloseTargetAccountCandidateAmbiguous.class,
            chart
                .validate(
                    new AccountDeclaration(
                        new AccountCode("3210"),
                        new AccountName("Replacement Result Holding"),
                        AccountType.EQUITY,
                        new AccountTaxonomy(
                            dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                            Optional.empty(),
                            Optional.empty(),
                            Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                            Optional.empty(),
                            Optional.empty())))
                .orElseThrow());

    assertEquals(
        List.of(new AccountCode("3200"), new AccountCode("3210")),
        rejection.candidateAccountCodes());
  }

  @Test
  void validateAllowsReplacingTheExistingSingularAccountDefinition() {
    ChartOfAccounts chart = ChartOfAccounts.of(List.of(resultHoldingAccount("3200", true)));

    assertEquals(
        Optional.empty(),
        chart.validate(
            new AccountDeclaration(
                new AccountCode("3200"),
                new AccountName("Updated Result Holding"),
                AccountType.EQUITY,
                financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING))));
  }

  private static RegisteredAccount assetAccount(
      String accountCode, Optional<AccountCode> parentAccountCode, boolean active) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName("Account " + accountCode),
        AccountType.ASSET,
        currentAssetTaxonomy(parentAccountCode),
        active,
        DECLARED_AT);
  }

  private static RegisteredAccount assetHeaderAccount(
      String accountCode, Optional<AccountCode> parentAccountCode, boolean active) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName("Header " + accountCode),
        AccountType.ASSET,
        currentAssetHeaderTaxonomy(parentAccountCode),
        active,
        DECLARED_AT);
  }

  private static AccountTaxonomy currentAssetTaxonomy(Optional<AccountCode> parentAccountCode) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        parentAccountCode,
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
  }

  private static AccountTaxonomy currentAssetContraTaxonomy(String contraOfAccountCode) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(new AccountCode(contraOfAccountCode)),
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
  }

  private static AccountTaxonomy nonCurrentAssetContraTaxonomy(String contraOfAccountCode) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(new AccountCode(contraOfAccountCode)),
        Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.NON_CASH));
  }

  private static AccountDeclaration contraDeclaration(
      String accountCode,
      String contraOfAccountCode,
      AccountType accountType,
      AccountTaxonomy accountTaxonomy) {
    assertEquals(
        new AccountCode(contraOfAccountCode), accountTaxonomy.contraOfAccountCode().orElseThrow());
    return new AccountDeclaration(
        new AccountCode(accountCode),
        new AccountName("Contra account " + accountCode),
        accountType,
        accountTaxonomy);
  }

  private static void assertContraViolation(
      ChartOfAccounts chart,
      AccountDeclaration declaration,
      ContraAccountRelationshipViolation expectedViolation) {
    assertEquals(
        expectedViolation,
        assertInstanceOf(ContraAccountInvalid.class, chart.validate(declaration).orElseThrow())
            .violation());
  }

  private static AccountTaxonomy currentAssetHeaderTaxonomy(
      Optional<AccountCode> parentAccountCode) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.HEADER,
        parentAccountCode,
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT));
  }

  private static AccountTaxonomy nonCurrentAssetTaxonomy(Optional<AccountCode> parentAccountCode) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        parentAccountCode,
        Optional.empty(),
        Optional.of(FinancialPositionLineClassification.NONCURRENT_ASSET),
        Optional.empty(),
        Optional.of(CashFlowAssetClassification.NON_CASH));
  }

  private static RegisteredAccount resultHoldingAccount(String accountCode, boolean active) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName("Result Holding " + accountCode),
        AccountType.EQUITY,
        financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
        active,
        DECLARED_AT);
  }
}
