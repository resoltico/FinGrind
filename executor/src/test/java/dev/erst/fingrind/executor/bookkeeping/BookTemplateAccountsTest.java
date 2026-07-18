package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookDoctrine;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Direct coverage for seed-template ownership on built-in book templates. */
class BookTemplateAccountsTest {
  @Test
  void declarations_publishStableStarterChartsForCashAndAccrualOwnerManagedServiceBooks() {
    List<AccountDeclaration> service =
        BookTemplateAccounts.declarations(BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE);
    List<AccountDeclaration> accrual =
        BookTemplateAccounts.declarations(
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL);

    assertEquals(7, service.size());
    assertEquals(15, accrual.size());
    assertTrue(
        service.stream()
            .anyMatch(
                declaration ->
                    declaration.accountCode().equals(new AccountCode("result-holding"))
                        && declaration
                                .accountTaxonomy()
                                .financialPositionLineClassification()
                                .orElseThrow()
                            == FinancialPositionLineClassification.RESULT_HOLDING));
    assertTrue(
        accrual.stream()
            .anyMatch(
                declaration ->
                    declaration.accountCode().equals(new AccountCode("accounts-receivable"))
                        && declaration
                                .accountTaxonomy()
                                .financialPositionLineClassification()
                                .orElseThrow()
                            == FinancialPositionLineClassification.TRADE_RECEIVABLE));
    assertEquals(
        ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE,
        declaration(accrual, "sales-discount-allowance")
            .accountTaxonomy()
            .profitAndLossLineClassification()
            .orElseThrow());
    assertEquals(
        ProfitAndLossLineClassification.BAD_DEBT_WRITE_OFF,
        declaration(accrual, "bad-debt-write-off")
            .accountTaxonomy()
            .profitAndLossLineClassification()
            .orElseThrow());
    assertEquals(
        FinancialPositionLineClassification.PREPAID_EXPENSE,
        declaration(accrual, "prepaid-expense")
            .accountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow());
    assertEquals(
        FinancialPositionLineClassification.DEFERRED_REVENUE,
        declaration(accrual, "deferred-revenue")
            .accountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow());
    assertEquals(
        FinancialPositionLineClassification.ACCRUED_EXPENSE,
        declaration(accrual, "accrued-expense")
            .accountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow());
  }

  @Test
  void declarations_publishInventoryAndGrossMarginAccountsForTradingBooks() {
    List<AccountDeclaration> cashTrading =
        BookTemplateAccounts.declarations(BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING);
    List<AccountDeclaration> accrualTrading =
        BookTemplateAccounts.declarations(
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL);

    assertEquals(13, cashTrading.size());
    assertEquals(20, accrualTrading.size());
    assertEquals(
        FinancialPositionLineClassification.INVENTORY,
        declaration(cashTrading, "inventory")
            .accountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow());
    assertEquals(
        new UnitOfMeasure("unit", 0), declaration(cashTrading, "inventory").unitOfMeasure());
    assertEquals(
        ProfitAndLossLineClassification.OPERATING_REVENUE,
        declaration(cashTrading, "sales-revenue")
            .accountTaxonomy()
            .profitAndLossLineClassification()
            .orElseThrow());
    assertEquals(
        ProfitAndLossLineClassification.COST_OF_SALES,
        declaration(cashTrading, "cost-of-sales")
            .accountTaxonomy()
            .profitAndLossLineClassification()
            .orElseThrow());
    assertNotNull(declaration(cashTrading, "inventory-write-down-loss"));
    assertNotNull(declaration(cashTrading, "inventory-shrinkage-loss"));
    assertNotNull(declaration(cashTrading, "inventory-count-gain"));
    assertEquals(
        FinancialPositionLineClassification.TRADE_RECEIVABLE,
        declaration(accrualTrading, "accounts-receivable")
            .accountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow());
    assertEquals(
        FinancialPositionLineClassification.TRADE_PAYABLE,
        declaration(accrualTrading, "accounts-payable")
            .accountTaxonomy()
            .financialPositionLineClassification()
            .orElseThrow());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("builtInDoctrines")
  void declarations_resolveEveryContraReferenceWithinItsSelectedChart(BookDoctrine doctrine) {
    List<AccountDeclaration> declarations = BookTemplateAccounts.declarations(doctrine);
    Set<AccountCode> declaredCodes =
        declarations.stream()
            .map(AccountDeclaration::accountCode)
            .collect(java.util.stream.Collectors.toSet());

    for (AccountDeclaration declaration : declarations) {
      var contraAccountCode = declaration.accountTaxonomy().contraOfAccountCode();
      if (contraAccountCode.isEmpty()) {
        continue;
      }
      AccountCode selectedContraAccountCode = contraAccountCode.orElseThrow();
      assertTrue(
          declaredCodes.contains(selectedContraAccountCode),
          () ->
              "Template "
                  + doctrine.bookTemplateId()
                  + " with basis "
                  + doctrine.accountingBasis()
                  + " declares contra account "
                  + declaration.accountCode().value()
                  + " against missing account "
                  + selectedContraAccountCode.value());
    }
  }

  private static Stream<BookDoctrine> builtInDoctrines() {
    return Stream.of(
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE_ACCRUAL,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL);
  }

  private static AccountDeclaration declaration(
      List<AccountDeclaration> declarations, String accountCode) {
    AccountDeclaration declaration =
        declarations.stream()
            .filter(candidate -> candidate.accountCode().equals(new AccountCode(accountCode)))
            .findFirst()
            .orElse(null);
    assertNotNull(declaration);
    return declaration;
  }
}
