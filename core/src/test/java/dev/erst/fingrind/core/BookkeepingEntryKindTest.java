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
    assertEquals("CASH_REVENUE", BookkeepingEntryKind.CASH_REVENUE.wireValue());
    assertEquals("CASH_EXPENSE", BookkeepingEntryKind.CASH_EXPENSE.wireValue());
    assertEquals("OWNER_CONTRIBUTION", BookkeepingEntryKind.OWNER_CONTRIBUTION.wireValue());
    assertEquals("OWNER_DRAW", BookkeepingEntryKind.OWNER_DRAW.wireValue());
    assertEquals("MANUAL_ADJUSTMENT", BookkeepingEntryKind.MANUAL_ADJUSTMENT.wireValue());
    assertEquals(
        List.of(
            "CASH_REVENUE",
            "CASH_EXPENSE",
            "OWNER_CONTRIBUTION",
            "OWNER_DRAW",
            "MANUAL_ADJUSTMENT"),
        BookkeepingEntryKind.wireValues());
  }

  @Test
  void fromWireValue_parsesStableVocabularyAndRejectsUnknownInput() {
    assertEquals(
        BookkeepingEntryKind.CASH_REVENUE, BookkeepingEntryKind.fromWireValue("CASH_REVENUE"));
    assertEquals(
        BookkeepingEntryKind.CASH_EXPENSE, BookkeepingEntryKind.fromWireValue("CASH_EXPENSE"));
    assertEquals(
        BookkeepingEntryKind.OWNER_CONTRIBUTION,
        BookkeepingEntryKind.fromWireValue("OWNER_CONTRIBUTION"));
    assertEquals(BookkeepingEntryKind.OWNER_DRAW, BookkeepingEntryKind.fromWireValue("OWNER_DRAW"));
    assertEquals(
        BookkeepingEntryKind.MANUAL_ADJUSTMENT,
        BookkeepingEntryKind.fromWireValue("MANUAL_ADJUSTMENT"));

    IllegalArgumentException unknownValueFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> BookkeepingEntryKind.fromWireValue("ACCRUAL_REVENUE"));
    assertEquals(
        "Unsupported bookkeeping entry kind: ACCRUAL_REVENUE", unknownValueFailure.getMessage());
    assertThrows(NullPointerException.class, () -> BookkeepingEntryKind.fromWireValue(nullOf()));
  }
}
