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
    assertEquals("EQUITY_CONTRIBUTION", BookkeepingEntryKind.EQUITY_CONTRIBUTION.wireValue());
    assertEquals("EQUITY_WITHDRAWAL", BookkeepingEntryKind.EQUITY_WITHDRAWAL.wireValue());
    assertEquals(
        "OPEN_ACCOUNTING_POSITION", BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION.wireValue());
    assertEquals("REVERSAL_ADJUSTMENT", BookkeepingEntryKind.REVERSAL_ADJUSTMENT.wireValue());
    assertEquals(
        List.of(
            "CASH_REVENUE",
            "CASH_EXPENSE",
            "EQUITY_CONTRIBUTION",
            "EQUITY_WITHDRAWAL",
            "OPEN_ACCOUNTING_POSITION",
            "REVERSAL_ADJUSTMENT"),
        BookkeepingEntryKind.wireValues());
  }

  @Test
  void fromWireValue_parsesStableVocabularyAndRejectsUnknownInput() {
    assertEquals(
        BookkeepingEntryKind.CASH_REVENUE, BookkeepingEntryKind.fromWireValue("CASH_REVENUE"));
    assertEquals(
        BookkeepingEntryKind.CASH_EXPENSE, BookkeepingEntryKind.fromWireValue("CASH_EXPENSE"));
    assertEquals(
        BookkeepingEntryKind.EQUITY_CONTRIBUTION,
        BookkeepingEntryKind.fromWireValue("EQUITY_CONTRIBUTION"));
    assertEquals(
        BookkeepingEntryKind.EQUITY_WITHDRAWAL,
        BookkeepingEntryKind.fromWireValue("EQUITY_WITHDRAWAL"));
    assertEquals(
        BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION,
        BookkeepingEntryKind.fromWireValue("OPEN_ACCOUNTING_POSITION"));
    assertEquals(
        BookkeepingEntryKind.REVERSAL_ADJUSTMENT,
        BookkeepingEntryKind.fromWireValue("REVERSAL_ADJUSTMENT"));

    IllegalArgumentException unknownValueFailure =
        assertThrows(
            IllegalArgumentException.class,
            () -> BookkeepingEntryKind.fromWireValue("ACCRUAL_REVENUE"));
    assertEquals(
        "Unsupported bookkeeping entry kind: ACCRUAL_REVENUE", unknownValueFailure.getMessage());
    assertThrows(NullPointerException.class, () -> BookkeepingEntryKind.fromWireValue(nullOf()));
  }
}
