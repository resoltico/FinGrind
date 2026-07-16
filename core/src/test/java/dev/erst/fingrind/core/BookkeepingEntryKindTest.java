package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for the canonical public bookkeeping entry vocabulary. */
class BookkeepingEntryKindTest {
  @Test
  void wireHelpers_publishStableVocabularyInDeclarationOrder() {
    assertEquals("DIRECT_JOURNAL", BookkeepingEntryKind.DIRECT_JOURNAL.wireValue());
    assertEquals("SALE_SETTLED", BookkeepingEntryKind.SALE_SETTLED.wireValue());
    assertEquals("SALE_ON_CREDIT", BookkeepingEntryKind.SALE_ON_CREDIT.wireValue());
    assertEquals("PURCHASE_SETTLED", BookkeepingEntryKind.PURCHASE_SETTLED.wireValue());
    assertEquals("PURCHASE_ON_CREDIT", BookkeepingEntryKind.PURCHASE_ON_CREDIT.wireValue());
    assertEquals(
        "INVENTORY_CAPITALIZATION_SETTLED",
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED.wireValue());
    assertEquals(
        "INVENTORY_CAPITALIZATION_ON_CREDIT",
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT.wireValue());
    assertEquals("INVENTORY_WRITE_DOWN", BookkeepingEntryKind.INVENTORY_WRITE_DOWN.wireValue());
    assertEquals("INVENTORY_SHRINKAGE", BookkeepingEntryKind.INVENTORY_SHRINKAGE.wireValue());
    assertEquals(
        "INVENTORY_COUNT_INCREASE", BookkeepingEntryKind.INVENTORY_COUNT_INCREASE.wireValue());
    assertEquals("PREPAYMENT", BookkeepingEntryKind.PREPAYMENT.wireValue());
    assertEquals("DEFERRED_REVENUE", BookkeepingEntryKind.DEFERRED_REVENUE.wireValue());
    assertEquals("ACCRUED_EXPENSE", BookkeepingEntryKind.ACCRUED_EXPENSE.wireValue());
    assertEquals(
        "ACCRUAL_CUTOFF_RECOGNITION", BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION.wireValue());
    assertEquals(
        "ACCRUED_EXPENSE_SETTLEMENT", BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT.wireValue());
    assertEquals(
        "LATVIAN_MONTHLY_PAYROLL", BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL.wireValue());
    assertEquals(
        "LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT",
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT.wireValue());
    assertEquals(
        "LATVIAN_PAYROLL_STATE_REMITTANCE",
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE.wireValue());
    assertEquals(
        "FIXED_ASSET_CAPITALIZATION", BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION.wireValue());
    assertEquals(
        "FIXED_ASSET_DEPRECIATION", BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION.wireValue());
    assertEquals("FIXED_ASSET_DISPOSAL", BookkeepingEntryKind.FIXED_ASSET_DISPOSAL.wireValue());
    assertEquals("FINANCING_BORROWING", BookkeepingEntryKind.FINANCING_BORROWING.wireValue());
    assertEquals(
        "FINANCING_PRINCIPAL_REPAYMENT",
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT.wireValue());
    assertEquals(
        "FINANCING_INTEREST_ACCRUAL", BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL.wireValue());
    assertEquals(
        "FINANCING_INTEREST_PAYMENT", BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT.wireValue());
    assertEquals(
        "FOREIGN_CURRENCY_OBLIGATION",
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION.wireValue());
    assertEquals(
        "REALIZED_FOREIGN_EXCHANGE_SETTLEMENT",
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT.wireValue());
    assertEquals("EXPENSE_SETTLED", BookkeepingEntryKind.EXPENSE_SETTLED.wireValue());
    assertEquals("EXPENSE_ON_CREDIT", BookkeepingEntryKind.EXPENSE_ON_CREDIT.wireValue());
    assertEquals("RECEIPT", BookkeepingEntryKind.RECEIPT.wireValue());
    assertEquals("PAYMENT", BookkeepingEntryKind.PAYMENT.wireValue());
    assertEquals("OWNER_CONTRIBUTION", BookkeepingEntryKind.OWNER_CONTRIBUTION.wireValue());
    assertEquals("OWNER_WITHDRAWAL", BookkeepingEntryKind.OWNER_WITHDRAWAL.wireValue());
    assertEquals("OPENING_POSITION", BookkeepingEntryKind.OPENING_POSITION.wireValue());
    assertEquals("REVERSAL", BookkeepingEntryKind.REVERSAL.wireValue());
    assertEquals(
        List.of(
            "DIRECT_JOURNAL",
            "SALE_SETTLED",
            "SALE_ON_CREDIT",
            "PURCHASE_SETTLED",
            "PURCHASE_ON_CREDIT",
            "INVENTORY_CAPITALIZATION_SETTLED",
            "INVENTORY_CAPITALIZATION_ON_CREDIT",
            "INVENTORY_WRITE_DOWN",
            "INVENTORY_SHRINKAGE",
            "INVENTORY_COUNT_INCREASE",
            "PREPAYMENT",
            "DEFERRED_REVENUE",
            "ACCRUED_EXPENSE",
            "ACCRUAL_CUTOFF_RECOGNITION",
            "ACCRUED_EXPENSE_SETTLEMENT",
            "LATVIAN_MONTHLY_PAYROLL",
            "LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT",
            "LATVIAN_PAYROLL_STATE_REMITTANCE",
            "FIXED_ASSET_CAPITALIZATION",
            "FIXED_ASSET_DEPRECIATION",
            "FIXED_ASSET_DISPOSAL",
            "FINANCING_BORROWING",
            "FINANCING_PRINCIPAL_REPAYMENT",
            "FINANCING_INTEREST_ACCRUAL",
            "FINANCING_INTEREST_PAYMENT",
            "FOREIGN_CURRENCY_OBLIGATION",
            "REALIZED_FOREIGN_EXCHANGE_SETTLEMENT",
            "EXPENSE_SETTLED",
            "EXPENSE_ON_CREDIT",
            "RECEIPT",
            "PAYMENT",
            "OWNER_CONTRIBUTION",
            "OWNER_WITHDRAWAL",
            "OPENING_POSITION",
            "REVERSAL"),
        BookkeepingEntryKind.wireValues());
  }

  @Test
  void fromWireValue_parsesStableVocabularyAndRejectsUnknownInput() {
    assertEquals(
        BookkeepingEntryKind.DIRECT_JOURNAL, BookkeepingEntryKind.fromWireValue("DIRECT_JOURNAL"));
    assertEquals(
        BookkeepingEntryKind.SALE_SETTLED, BookkeepingEntryKind.fromWireValue("SALE_SETTLED"));
    assertEquals(
        BookkeepingEntryKind.SALE_ON_CREDIT, BookkeepingEntryKind.fromWireValue("SALE_ON_CREDIT"));
    assertEquals(
        BookkeepingEntryKind.PURCHASE_SETTLED,
        BookkeepingEntryKind.fromWireValue("PURCHASE_SETTLED"));
    assertEquals(
        BookkeepingEntryKind.PURCHASE_ON_CREDIT,
        BookkeepingEntryKind.fromWireValue("PURCHASE_ON_CREDIT"));
    assertEquals(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
        BookkeepingEntryKind.fromWireValue("INVENTORY_CAPITALIZATION_SETTLED"));
    assertEquals(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        BookkeepingEntryKind.fromWireValue("INVENTORY_CAPITALIZATION_ON_CREDIT"));
    assertEquals(
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
        BookkeepingEntryKind.fromWireValue("INVENTORY_WRITE_DOWN"));
    assertEquals(
        BookkeepingEntryKind.INVENTORY_SHRINKAGE,
        BookkeepingEntryKind.fromWireValue("INVENTORY_SHRINKAGE"));
    assertEquals(
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
        BookkeepingEntryKind.fromWireValue("INVENTORY_COUNT_INCREASE"));
    assertEquals(BookkeepingEntryKind.PREPAYMENT, BookkeepingEntryKind.fromWireValue("PREPAYMENT"));
    assertEquals(
        BookkeepingEntryKind.DEFERRED_REVENUE,
        BookkeepingEntryKind.fromWireValue("DEFERRED_REVENUE"));
    assertEquals(
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        BookkeepingEntryKind.fromWireValue("ACCRUED_EXPENSE"));
    assertEquals(
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        BookkeepingEntryKind.fromWireValue("ACCRUAL_CUTOFF_RECOGNITION"));
    assertEquals(
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        BookkeepingEntryKind.fromWireValue("ACCRUED_EXPENSE_SETTLEMENT"));
    assertEquals(
        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
        BookkeepingEntryKind.fromWireValue("LATVIAN_MONTHLY_PAYROLL"));
    assertEquals(
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        BookkeepingEntryKind.fromWireValue("LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT"));
    assertEquals(
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
        BookkeepingEntryKind.fromWireValue("LATVIAN_PAYROLL_STATE_REMITTANCE"));
    assertEquals(
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
        BookkeepingEntryKind.fromWireValue("FIXED_ASSET_CAPITALIZATION"));
    assertEquals(
        BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
        BookkeepingEntryKind.fromWireValue("FIXED_ASSET_DEPRECIATION"));
    assertEquals(
        BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
        BookkeepingEntryKind.fromWireValue("FIXED_ASSET_DISPOSAL"));
    assertEquals(
        BookkeepingEntryKind.FINANCING_BORROWING,
        BookkeepingEntryKind.fromWireValue("FINANCING_BORROWING"));
    assertEquals(
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        BookkeepingEntryKind.fromWireValue("FINANCING_PRINCIPAL_REPAYMENT"));
    assertEquals(
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        BookkeepingEntryKind.fromWireValue("FINANCING_INTEREST_ACCRUAL"));
    assertEquals(
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        BookkeepingEntryKind.fromWireValue("FINANCING_INTEREST_PAYMENT"));
    assertEquals(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        BookkeepingEntryKind.fromWireValue("FOREIGN_CURRENCY_OBLIGATION"));
    assertEquals(
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        BookkeepingEntryKind.fromWireValue("REALIZED_FOREIGN_EXCHANGE_SETTLEMENT"));
    assertEquals(
        BookkeepingEntryKind.EXPENSE_SETTLED,
        BookkeepingEntryKind.fromWireValue("EXPENSE_SETTLED"));
    assertEquals(
        BookkeepingEntryKind.EXPENSE_ON_CREDIT,
        BookkeepingEntryKind.fromWireValue("EXPENSE_ON_CREDIT"));
    assertEquals(BookkeepingEntryKind.RECEIPT, BookkeepingEntryKind.fromWireValue("RECEIPT"));
    assertEquals(BookkeepingEntryKind.PAYMENT, BookkeepingEntryKind.fromWireValue("PAYMENT"));
    assertEquals(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        BookkeepingEntryKind.fromWireValue("OWNER_CONTRIBUTION"));
    assertEquals(
        BookkeepingEntryKind.OWNER_WITHDRAWAL,
        BookkeepingEntryKind.fromWireValue("OWNER_WITHDRAWAL"));
    assertEquals(
        BookkeepingEntryKind.OPENING_POSITION,
        BookkeepingEntryKind.fromWireValue("OPENING_POSITION"));
    assertEquals(BookkeepingEntryKind.REVERSAL, BookkeepingEntryKind.fromWireValue("REVERSAL"));

    IllegalArgumentException unknownValueFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> BookkeepingEntryKind.fromWireValue("ACCRUAL_REVENUE"));
    assertEquals(
        "Unsupported bookkeeping entry kind: ACCRUAL_REVENUE", unknownValueFailure.getMessage());
    assertThrows(NullPointerException.class, () -> BookkeepingEntryKind.fromWireValue(nullOf()));
  }

  @Test
  void narrativeLabel_returnsStableOperatorLanguage() {
    assertEquals("direct journal", BookkeepingEntryKind.DIRECT_JOURNAL.narrativeLabel());
    assertEquals("settled sale", BookkeepingEntryKind.SALE_SETTLED.narrativeLabel());
    assertEquals("sale on credit", BookkeepingEntryKind.SALE_ON_CREDIT.narrativeLabel());
    assertEquals("settled purchase", BookkeepingEntryKind.PURCHASE_SETTLED.narrativeLabel());
    assertEquals("purchase on credit", BookkeepingEntryKind.PURCHASE_ON_CREDIT.narrativeLabel());
    assertEquals(
        "settled inventory capitalization",
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED.narrativeLabel());
    assertEquals(
        "inventory capitalization on credit",
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT.narrativeLabel());
    assertEquals(
        "inventory write-down", BookkeepingEntryKind.INVENTORY_WRITE_DOWN.narrativeLabel());
    assertEquals("inventory shrinkage", BookkeepingEntryKind.INVENTORY_SHRINKAGE.narrativeLabel());
    assertEquals(
        "inventory count increase", BookkeepingEntryKind.INVENTORY_COUNT_INCREASE.narrativeLabel());
    assertEquals("prepayment", BookkeepingEntryKind.PREPAYMENT.narrativeLabel());
    assertEquals("deferred revenue", BookkeepingEntryKind.DEFERRED_REVENUE.narrativeLabel());
    assertEquals("accrued expense", BookkeepingEntryKind.ACCRUED_EXPENSE.narrativeLabel());
    assertEquals(
        "accrual cut-off recognition",
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION.narrativeLabel());
    assertEquals(
        "accrued-expense settlement",
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT.narrativeLabel());
    assertEquals(
        "Latvian monthly payroll", BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL.narrativeLabel());
    assertEquals(
        "Latvian payroll net-wage settlement",
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT.narrativeLabel());
    assertEquals(
        "Latvian payroll state remittance",
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE.narrativeLabel());
    assertEquals(
        "fixed-asset capitalization",
        BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION.narrativeLabel());
    assertEquals(
        "fixed-asset depreciation", BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION.narrativeLabel());
    assertEquals(
        "fixed-asset disposal", BookkeepingEntryKind.FIXED_ASSET_DISPOSAL.narrativeLabel());
    assertEquals("financing borrowing", BookkeepingEntryKind.FINANCING_BORROWING.narrativeLabel());
    assertEquals(
        "financing principal repayment",
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT.narrativeLabel());
    assertEquals(
        "financing interest accrual",
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL.narrativeLabel());
    assertEquals(
        "financing interest payment",
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT.narrativeLabel());
    assertEquals(
        "foreign-currency obligation",
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION.narrativeLabel());
    assertEquals(
        "realized foreign-exchange settlement",
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT.narrativeLabel());
    assertEquals("settled expense", BookkeepingEntryKind.EXPENSE_SETTLED.narrativeLabel());
    assertEquals("expense on credit", BookkeepingEntryKind.EXPENSE_ON_CREDIT.narrativeLabel());
    assertEquals("receipt", BookkeepingEntryKind.RECEIPT.narrativeLabel());
    assertEquals("payment", BookkeepingEntryKind.PAYMENT.narrativeLabel());
    assertEquals("owner contribution", BookkeepingEntryKind.OWNER_CONTRIBUTION.narrativeLabel());
    assertEquals("owner withdrawal", BookkeepingEntryKind.OWNER_WITHDRAWAL.narrativeLabel());
    assertEquals("opening position", BookkeepingEntryKind.OPENING_POSITION.narrativeLabel());
    assertEquals("reversal", BookkeepingEntryKind.REVERSAL.narrativeLabel());
  }
}
