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
    assertEquals("SALE", BookkeepingEntryKind.SALE.wireValue());
    assertEquals("EXPENSE", BookkeepingEntryKind.EXPENSE.wireValue());
    assertEquals("OWNER_CONTRIBUTION", BookkeepingEntryKind.OWNER_CONTRIBUTION.wireValue());
    assertEquals("OWNER_WITHDRAWAL", BookkeepingEntryKind.OWNER_WITHDRAWAL.wireValue());
    assertEquals("OPENING_POSITION", BookkeepingEntryKind.OPENING_POSITION.wireValue());
    assertEquals("REVERSAL", BookkeepingEntryKind.REVERSAL.wireValue());
    assertEquals(
        List.of(
            "DIRECT_JOURNAL",
            "SALE",
            "EXPENSE",
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
    assertEquals(BookkeepingEntryKind.SALE, BookkeepingEntryKind.fromWireValue("SALE"));
    assertEquals(BookkeepingEntryKind.EXPENSE, BookkeepingEntryKind.fromWireValue("EXPENSE"));
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
    assertEquals("sale", BookkeepingEntryKind.SALE.narrativeLabel());
    assertEquals("expense", BookkeepingEntryKind.EXPENSE.narrativeLabel());
    assertEquals("owner contribution", BookkeepingEntryKind.OWNER_CONTRIBUTION.narrativeLabel());
    assertEquals("owner withdrawal", BookkeepingEntryKind.OWNER_WITHDRAWAL.narrativeLabel());
    assertEquals("opening position", BookkeepingEntryKind.OPENING_POSITION.narrativeLabel());
    assertEquals("reversal", BookkeepingEntryKind.REVERSAL.narrativeLabel());
  }
}
