package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers the stable wire vocabulary for {@link PostingOriginKind}. */
class PostingOriginKindTest {
  @Test
  void wireValues_areStableAndRoundTrip() {
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
            "REVERSAL",
            "INTERIM_RESULT_SWEEP",
            "FISCAL_YEAR_CLOSE"),
        PostingOriginKind.wireValues());
    for (PostingOriginKind postingOriginKind : PostingOriginKind.values()) {
      assertEquals(
          postingOriginKind, PostingOriginKind.fromWireValue(postingOriginKind.wireValue()));
    }
  }

  @Test
  void fromWireValue_rejectsNullAndUnknownValues() {
    assertThrows(NullPointerException.class, () -> PostingOriginKind.fromWireValue(nullOf()));
    assertThrows(
        IllegalArgumentException.class, () -> PostingOriginKind.fromWireValue("MANUAL_ADJUSTMENT"));
  }
}
