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
