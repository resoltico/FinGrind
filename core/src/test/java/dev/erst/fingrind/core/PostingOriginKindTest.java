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
            "CASH_REVENUE",
            "CASH_EXPENSE",
            "EQUITY_CONTRIBUTION",
            "EQUITY_WITHDRAWAL",
            "OPENING_BALANCE_ADJUSTMENT",
            "CORRECTION_ADJUSTMENT",
            "REVERSAL_ADJUSTMENT",
            "PERIOD_RESULT_TRANSFER"),
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
