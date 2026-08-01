package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import org.junit.jupiter.api.Test;

/** Tests the canonical public refusals for lifecycle reversal dependencies. */
class PostingOwnedLifecycleRejectionSemanticsTest {
  @Test
  void lifecycleReversalRefusalsNameTheBlockedAggregateAndPriorPostingField() {
    PostingRejection.EntrySemanticsViolation fixedAsset =
        PostingFixedAssetRejectionSemantics.capitalizationReversalRequiresApplicationsReversed(
            BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION, new FixedAssetId("delivery-van"));
    PostingRejection.EntrySemanticsViolation financing =
        PostingFinancingRejectionSemantics.borrowingReversalRequiresApplicationsReversed(
            BookkeepingEntryKind.FINANCING_BORROWING, new FinancingArrangementId("term-loan"));
    PostingRejection.EntrySemanticsViolation foreignExchange =
        PostingRealizedForeignExchangeRejectionSemantics
            .obligationReversalRequiresSettlementReversed(
                BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
                new ForeignCurrencyObligationId("invoice-1"));

    assertEquals(
        "fixed-asset-capitalization-reversal-requires-applications-reversed", fixedAsset.code());
    assertEquals("financing-borrowing-reversal-requires-applications-reversed", financing.code());
    assertEquals(
        "foreign-currency-obligation-reversal-requires-settlement-reversed",
        foreignExchange.code());
    assertEquals("reversal.priorPostingId", fixedAsset.field());
    assertEquals("reversal.priorPostingId", financing.field());
    assertEquals("reversal.priorPostingId", foreignExchange.field());
    assertTrue(fixedAsset.message().contains("delivery-van"));
    assertTrue(financing.message().contains("term-loan"));
    assertTrue(foreignExchange.message().contains("invoice-1"));
    assertTrue(fixedAsset.message().contains("FIXED_ASSET_CAPITALIZATION"));
    assertTrue(financing.message().contains("FINANCING_BORROWING"));
    assertTrue(foreignExchange.message().contains("FOREIGN_CURRENCY_OBLIGATION"));
  }
}
