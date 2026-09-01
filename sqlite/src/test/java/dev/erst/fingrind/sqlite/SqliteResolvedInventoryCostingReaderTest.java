package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Durable replay coverage for exact resolved inventory costing on costed sale readback. */
class SqliteResolvedInventoryCostingReaderTest extends SqliteNativeBridgeTestSupport {
  @Test
  void purchaseReadback_reconstructsThePersistedAcquisitionCostingFacts() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("costed-purchase-readback.sqlite")))) {
      createInventoryCostingTables(database);
      database.executeStatement("insert into account values ('inventory', 0)");
      database.executeStatement(
          """
          insert into inventory_movement
          values ('purchase', 'inventory', '2026-05-04', 1, 'ACQUISITION', 10, 12000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("purchase")));

      try (SqliteNativeStatement postingRow = purchasePostingRow(database)) {
        assertEquals(SqliteNativeResultCode.code("ROW"), postingRow.step());
        ResolvedInventoryAcquisition resolved =
            Objects.requireNonNull(
                SqliteResolvedInventoryCostingReader.resolvedAcquisition(
                    database,
                    new PostingId(SqliteTestPostingIds.valueForLabel("purchase")),
                    postingRow),
                "resolvedInventoryAcquisition");

        assertEquals(Quantity.ofScaledUnits(0, 10L), resolved.quantityAcquired());
        assertEquals(new MonetaryAmount("EUR", "10000"), resolved.preTaxCost());
        assertEquals(new MonetaryAmount("EUR", "12000"), resolved.carryingCost());
      }
    }
  }

  @Test
  void purchaseReadback_returnsNoAcquisitionWhenNoAcquisitionMovementExists() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("costed-purchase-missing.sqlite")))) {
      createInventoryCostingTables(database);
      database.executeStatement("insert into account values ('inventory', 0)");

      try (SqliteNativeStatement postingRow = purchasePostingRow(database)) {
        assertEquals(SqliteNativeResultCode.code("ROW"), postingRow.step());
        assertNull(
            SqliteResolvedInventoryCostingReader.resolvedAcquisition(
                database,
                new PostingId(SqliteTestPostingIds.valueForLabel("missing-purchase")),
                postingRow));
      }
    }
  }

  @Test
  void purchaseReadback_rejectsDuplicateAndNonIncreasingAcquisitionMovements() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("costed-purchase-invalid.sqlite")))) {
      createInventoryCostingTables(database);
      database.executeStatement("insert into account values ('inventory', 0)");

      assertAcquisitionRejection(
          database,
          "duplicate-purchase",
          """
          insert into inventory_movement
          values ('purchase-one', 'inventory', '2026-05-04', 1, 'ACQUISITION', 10, 10000, '%s');
          insert into inventory_movement
          values ('purchase-two', 'inventory', '2026-05-04', 2, 'ACQUISITION', 1, 1000, '%s')
          """
              .formatted(
                  SqliteTestPostingIds.valueForLabel("duplicate-purchase"),
                  SqliteTestPostingIds.valueForLabel("duplicate-purchase")),
          "Inventory acquisition must resolve exactly one inventory movement.");
      assertAcquisitionRejection(
          database,
          "zero-quantity-purchase",
          """
          insert into inventory_movement
          values ('purchase-zero-quantity', 'inventory', '2026-05-04', 3, 'ACQUISITION', 0, 1000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("zero-quantity-purchase")),
          "Inventory acquisition movement must increase quantity and carrying cost.");
      assertAcquisitionRejection(
          database,
          "zero-cost-purchase",
          """
          insert into inventory_movement
          values ('purchase-zero-cost', 'inventory', '2026-05-04', 4, 'ACQUISITION', 1, 0, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("zero-cost-purchase")),
          "Inventory acquisition movement must increase quantity and carrying cost.");
    }
  }

  @Test
  void settledSaleReadback_reconstructsExactCostingFromCanonicalMovementReplay() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("costed-sale-readback.sqlite")))) {
      createInventoryCostingTables(database);
      seedPurchase(database);
      database.executeStatement(
          """
          insert into inventory_movement
          values ('sale', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -4000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("sale")));

      BookkeepingEntry.SaleSettled resolved =
          assertInstanceOf(
              BookkeepingEntry.SaleSettled.class,
              SqliteResolvedInventoryCostingReader.resolve(
                  database,
                  new PostingId(SqliteTestPostingIds.valueForLabel("sale")),
                  saleSettledWithInventoryRelief()));
      var resolvedCosting =
          Objects.requireNonNull(resolved.resolvedInventoryCosting(), "resolvedInventoryCosting");

      assertEquals(Money.parse("EUR", "40.00"), resolvedCosting.costOfSales());
      assertEquals(Quantity.ofScaledUnits(0, 4L), resolvedCosting.quantityRelieved());
      assertEquals(
          Money.parse("EUR", "10.00"), resolvedCosting.roundedMovingAverageUnitCostProjection());
    }
  }

  @Test
  void creditSaleReadback_reconstructsExactCostingFromCanonicalMovementReplay() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(
            bookAccess(tempDirectory.resolve("costed-credit-sale-readback.sqlite")))) {
      createInventoryCostingTables(database);
      seedPurchase(database);
      database.executeStatement(
          """
          insert into inventory_movement
          values ('sale', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -4000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("sale")));

      BookkeepingEntry.SaleOnCredit resolved =
          assertInstanceOf(
              BookkeepingEntry.SaleOnCredit.class,
              SqliteResolvedInventoryCostingReader.resolve(
                  database,
                  new PostingId(SqliteTestPostingIds.valueForLabel("sale")),
                  saleOnCreditWithInventoryRelief()));

      assertEquals(
          Money.parse("EUR", "40.00"),
          Objects.requireNonNull(resolved.resolvedInventoryCosting(), "resolvedInventoryCosting")
              .costOfSales());
    }
  }

  @Test
  void readback_returnsNoResolutionWhenNoDisposalExistsAndForNoOriginatingEntry() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("costed-sale-no-disposal.sqlite")))) {
      createInventoryCostingTables(database);
      database.executeStatement("insert into account values ('inventory', 0)");

      assertNull(
          SqliteResolvedInventoryCostingReader.resolve(
              database,
              new PostingId(SqliteTestPostingIds.valueForLabel("no-disposal")),
              saleOnCreditWithInventoryRelief()));
      assertNull(
          SqliteResolvedInventoryCostingReader.resolve(
              database, new PostingId(SqliteTestPostingIds.valueForLabel("no-disposal")), null));
    }
  }

  @Test
  void readback_rejectsAmbiguousInvalidAndInconsistentDurableDisposals() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(
            bookAccess(tempDirectory.resolve("costed-sale-invalid-disposals.sqlite")))) {
      createInventoryCostingTables(database);
      seedPurchase(database);
      database.executeStatement(
          """
          insert into inventory_movement
          values ('sale-one', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -4000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("ambiguous")));
      database.executeStatement(
          """
          insert into inventory_movement
          values ('sale-two', 'inventory', '2026-05-05', 3, 'DISPOSAL', -1, -1000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("ambiguous")));

      assertRejection(
          database,
          "ambiguous",
          "Costed sale must resolve exactly one inventory disposal movement.");

      database.executeStatement(
          "delete from inventory_movement where posting_id = '%s'"
              .formatted(SqliteTestPostingIds.valueForLabel("ambiguous")));
      database.executeStatement(
          """
          insert into inventory_movement
          values ('sale-invalid', 'inventory', '2026-05-05', 2, 'DISPOSAL', 4, -4000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("invalid")));
      assertRejection(
          database,
          "invalid",
          "Costed sale inventory disposal movement must decrease quantity and carrying cost.");

      database.executeStatement(
          "delete from inventory_movement where posting_id = '%s'"
              .formatted(SqliteTestPostingIds.valueForLabel("invalid")));
      database.executeStatement(
          """
          insert into inventory_movement
          values ('sale-invalid-cost', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, 4000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("invalid-cost")));
      assertRejection(
          database,
          "invalid-cost",
          "Costed sale inventory disposal movement must decrease quantity and carrying cost.");

      database.executeStatement(
          "delete from inventory_movement where posting_id = '%s'"
              .formatted(SqliteTestPostingIds.valueForLabel("invalid-cost")));
      database.executeStatement(
          """
          insert into inventory_movement
          values ('sale-inconsistent', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -3000, '%s')
          """
              .formatted(SqliteTestPostingIds.valueForLabel("inconsistent")));
      assertRejection(
          database,
          "inconsistent",
          "Persisted inventory disposal cost does not match exact weighted-average replay.");
    }
  }

  private static void assertRejection(
      SqliteNativeDatabase database, String postingId, String expectedMessage) {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteResolvedInventoryCostingReader.resolve(
                    database,
                    new PostingId(SqliteTestPostingIds.valueForLabel(postingId)),
                    saleOnCreditWithInventoryRelief()));
    assertEquals(expectedMessage, exception.getMessage());
  }

  private static void assertAcquisitionRejection(
      SqliteNativeDatabase database, String postingId, String insertSql, String expectedMessage) {
    database.executeScript(insertSql + ";");
    try (SqliteNativeStatement postingRow = purchasePostingRow(database)) {
      assertEquals(SqliteNativeResultCode.code("ROW"), postingRow.step());
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteResolvedInventoryCostingReader.resolvedAcquisition(
                      database,
                      new PostingId(SqliteTestPostingIds.valueForLabel(postingId)),
                      postingRow));
      assertEquals(expectedMessage, exception.getMessage());
    }
  }

  private static SqliteNativeStatement purchasePostingRow(SqliteNativeDatabase database) {
    return database.prepare(
        """
        select
            'purchase-posting', 'STANDARD', 'PURCHASE_SETTLED', 'inventory', 'supplier', null,
            'EUR', 12000, null, '10', 'EUR', 1000
        """);
  }

  private static void createInventoryCostingTables(SqliteNativeDatabase database) {
    database.executeStatement(
        "create table account (account_code text primary key, quantity_scale integer not null)");
    database.executeStatement(
        "create table inventory_movement (movement_id text primary key, inventory_account text not null, effective_date text not null, account_sequence integer not null, kind text not null, quantity_delta integer not null, cost_delta_minor integer not null, posting_id text not null)");
  }

  private static void seedPurchase(SqliteNativeDatabase database) {
    database.executeStatement("insert into account values ('inventory', 0)");
    database.executeStatement(
        """
        insert into inventory_movement
        values ('purchase', 'inventory', '2026-05-04', 1, 'ACQUISITION', 10, 10000, '%s')
        """
            .formatted(SqliteTestPostingIds.valueForLabel("purchase")));
  }

  private static BookkeepingEntry.SaleSettled saleSettledWithInventoryRelief() {
    return new BookkeepingEntry.SaleSettled(
        java.time.LocalDate.parse("2026-05-05"),
        new AccountCode("1000"),
        new AccountCode("2000"),
        new MonetaryAmount("EUR", "12500"),
        inventoryRelief(),
        null,
        null,
        null,
        null);
  }

  private static BookkeepingEntry.SaleOnCredit saleOnCreditWithInventoryRelief() {
    return new BookkeepingEntry.SaleOnCredit(
        java.time.LocalDate.parse("2026-05-05"),
        new AccountCode("1100"),
        new AccountCode("2000"),
        new MonetaryAmount("EUR", "12500"),
        inventoryRelief(),
        null,
        null,
        null,
        null);
  }

  private static InventoryRelief inventoryRelief() {
    return new InventoryRelief(
        new AccountCode("inventory"),
        new AccountCode("5000"),
        new dev.erst.fingrind.contract.bookkeeping.QuantityText("4"));
  }
}
