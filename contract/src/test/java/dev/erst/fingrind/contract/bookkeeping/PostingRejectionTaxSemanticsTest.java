package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Direct coverage for canonical tax-specific posting rejection details. */
class PostingRejectionTaxSemanticsTest {
  @Test
  void taxCompositionMoneyRangeExceeded_preservesThePublishedTaxEntrySemantics() {
    PostingRejection.EntrySemanticsViolation violation =
        PostingRejectionSemantics.taxCompositionMoneyRangeExceeded("SALE");

    assertEquals("tax-composition-money-range-exceeded", violation.code());
    assertEquals("amount", violation.field());
    assertTrue(violation.message().contains("SALE"));
  }
}
