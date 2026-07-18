package dev.erst.fingrind.contract.protocol;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Contract tests for the canonical print-request-template topic inventory. */
class ProtocolRequestTemplateTopicsTest {
  @Test
  void topicsRemainInStablePublicOrder() {
    assertEquals(
        List.of(
            OperationId.POST_ENTRY,
            OperationId.PREFLIGHT_ENTRY,
            OperationId.RECORD_SALE_SETTLED,
            OperationId.RECORD_SALE_ON_CREDIT,
            OperationId.RECORD_PURCHASE_SETTLED,
            OperationId.RECORD_PURCHASE_ON_CREDIT,
            OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
            OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
            OperationId.RECORD_INVENTORY_WRITE_DOWN,
            OperationId.RECORD_INVENTORY_SHRINKAGE,
            OperationId.RECORD_INVENTORY_COUNT_INCREASE,
            OperationId.RECORD_PREPAYMENT,
            OperationId.RECORD_DEFERRED_REVENUE,
            OperationId.RECORD_ACCRUED_EXPENSE,
            OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
            OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
            OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL,
            OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
            OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
            OperationId.RECORD_FIXED_ASSET_CAPITALIZATION,
            OperationId.RECORD_FIXED_ASSET_DEPRECIATION,
            OperationId.RECORD_FIXED_ASSET_DISPOSAL,
            OperationId.RECORD_FINANCING_BORROWING,
            OperationId.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
            OperationId.RECORD_FINANCING_INTEREST_ACCRUAL,
            OperationId.RECORD_FINANCING_INTEREST_PAYMENT,
            OperationId.RECORD_FOREIGN_CURRENCY_OBLIGATION,
            OperationId.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
            OperationId.RECORD_EXPENSE_SETTLED,
            OperationId.RECORD_EXPENSE_ON_CREDIT,
            OperationId.RECORD_RECEIPT,
            OperationId.RECORD_PAYMENT,
            OperationId.RECORD_OWNER_CONTRIBUTION,
            OperationId.RECORD_OWNER_WITHDRAWAL,
            OperationId.RECORD_OPENING_POSITION,
            OperationId.RECORD_REVERSAL,
            OperationId.DECLARE_ACCOUNT,
            OperationId.AMEND_ACCOUNT,
            OperationId.RETIRE_ACCOUNT,
            OperationId.DECLARE_TAX_REGISTRATION),
        ProtocolRequestTemplateTopics.topics());
    assertEquals(
        List.of(
            "post-entry",
            "preflight-entry",
            "record-sale-settled",
            "record-sale-on-credit",
            "record-purchase-settled",
            "record-purchase-on-credit",
            "record-inventory-capitalization-settled",
            "record-inventory-capitalization-on-credit",
            "record-inventory-write-down",
            "record-inventory-shrinkage",
            "record-inventory-count-increase",
            "record-prepayment",
            "record-deferred-revenue",
            "record-accrued-expense",
            "record-accrual-cutoff-recognition",
            "record-accrued-expense-settlement",
            "record-latvian-monthly-payroll",
            "record-latvian-payroll-net-wage-settlement",
            "record-latvian-payroll-state-remittance",
            "record-fixed-asset-capitalization",
            "record-fixed-asset-depreciation",
            "record-fixed-asset-disposal",
            "record-financing-borrowing",
            "record-financing-principal-repayment",
            "record-financing-interest-accrual",
            "record-financing-interest-payment",
            "record-foreign-currency-obligation",
            "record-realized-foreign-exchange-settlement",
            "record-expense-settled",
            "record-expense-on-credit",
            "record-receipt",
            "record-payment",
            "record-owner-contribution",
            "record-owner-withdrawal",
            "record-opening-position",
            "record-reversal",
            "declare-account",
            "amend-account",
            "retire-account",
            "declare-tax-registration"),
        ProtocolRequestTemplateTopics.topicNames());
    assertEquals(
        "[post-entry|preflight-entry|record-sale-settled|record-sale-on-credit|record-purchase-settled|record-purchase-on-credit|record-inventory-capitalization-settled|record-inventory-capitalization-on-credit|record-inventory-write-down|record-inventory-shrinkage|record-inventory-count-increase|record-prepayment|record-deferred-revenue|record-accrued-expense|record-accrual-cutoff-recognition|record-accrued-expense-settlement|record-latvian-monthly-payroll|record-latvian-payroll-net-wage-settlement|record-latvian-payroll-state-remittance|record-fixed-asset-capitalization|record-fixed-asset-depreciation|record-fixed-asset-disposal|record-financing-borrowing|record-financing-principal-repayment|record-financing-interest-accrual|record-financing-interest-payment|record-foreign-currency-obligation|record-realized-foreign-exchange-settlement|record-expense-settled|record-expense-on-credit|record-receipt|record-payment|record-owner-contribution|record-owner-withdrawal|record-opening-position|record-reversal|declare-account|amend-account|retire-account|declare-tax-registration]",
        ProtocolRequestTemplateTopics.syntax());
  }

  @Test
  void supportsOnlyRegisteredRequestTemplateTopics() {
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.RECORD_SALE_SETTLED));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.RECORD_PURCHASE_SETTLED));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL));
    assertTrue(
        ProtocolRequestTemplateTopics.supports(
            OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT));
    assertTrue(
        ProtocolRequestTemplateTopics.supports(
            OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.DECLARE_ACCOUNT));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.AMEND_ACCOUNT));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.RETIRE_ACCOUNT));
    assertTrue(ProtocolRequestTemplateTopics.supports(OperationId.DECLARE_TAX_REGISTRATION));
    assertFalse(ProtocolRequestTemplateTopics.supports(OperationId.EXECUTE_PLAN));
  }

  @Test
  void nullOperationIsRejected() {
    assertThrows(
        NullPointerException.class,
        () -> ProtocolRequestTemplateTopics.supports(nullOf(OperationId.class)));
  }
}
