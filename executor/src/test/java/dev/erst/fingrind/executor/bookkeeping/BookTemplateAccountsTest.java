package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.List;
import org.junit.jupiter.api.Test;

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
    assertEquals(12, accrual.size());
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
  }

  @Test
  void declarations_publishInventoryAndGrossMarginAccountsForTradingBooks() {
    List<AccountDeclaration> cashTrading =
        BookTemplateAccounts.declarations(BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING);
    List<AccountDeclaration> accrualTrading =
        BookTemplateAccounts.declarations(
            BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING_ACCRUAL);

    assertEquals(13, cashTrading.size());
    assertEquals(17, accrualTrading.size());
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
