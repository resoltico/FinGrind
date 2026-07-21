package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.Quantity;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Durable replay coverage for exact resolved inventory costing on costed sale readback. */
class SqliteResolvedInventoryCostingReaderTest extends SqliteNativeBridgeTestSupport {
  @Test
  void settledSaleReadback_reconstructsExactCostingFromCanonicalMovementReplay() throws Exception {
    try (SqliteNativeDatabase database =
        openNativeDatabase(bookAccess(tempDirectory.resolve("costed-sale-readback.sqlite")))) {
      createInventoryCostingTables(database);
      seedPurchase(database);
      database.executeStatement(
          "insert into inventory_movement values ('sale', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -4000, 'sale')");

      BookkeepingEntry.SaleSettled resolved =
          assertInstanceOf(
              BookkeepingEntry.SaleSettled.class,
              SqliteResolvedInventoryCostingReader.resolve(
                  database,
                  new PostingId("099c15e8-f223-31cf-a21c-382e45f9e9cb"),
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
          "insert into inventory_movement values ('sale', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -4000, 'sale')");

      BookkeepingEntry.SaleOnCredit resolved =
          assertInstanceOf(
              BookkeepingEntry.SaleOnCredit.class,
              SqliteResolvedInventoryCostingReader.resolve(
                  database,
                  new PostingId("099c15e8-f223-31cf-a21c-382e45f9e9cb"),
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
              new PostingId("092aa3d9-cdb7-3194-a11e-754cc34214df"),
              saleOnCreditWithInventoryRelief()));
      assertNull(
          SqliteResolvedInventoryCostingReader.resolve(
              database, new PostingId("092aa3d9-cdb7-3194-a11e-754cc34214df"), null));
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
          "insert into inventory_movement values ('sale-one', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -4000, 'ambiguous')");
      database.executeStatement(
          "insert into inventory_movement values ('sale-two', 'inventory', '2026-05-05', 3, 'DISPOSAL', -1, -1000, 'ambiguous')");

      assertRejection(
          database,
          "ambiguous",
          "Costed sale must resolve exactly one inventory disposal movement.");

      database.executeStatement("delete from inventory_movement where posting_id = 'ambiguous'");
      database.executeStatement(
          "insert into inventory_movement values ('sale-invalid', 'inventory', '2026-05-05', 2, 'DISPOSAL', 4, -4000, 'invalid')");
      assertRejection(
          database,
          "invalid",
          "Costed sale inventory disposal movement must decrease quantity and carrying cost.");

      database.executeStatement("delete from inventory_movement where posting_id = 'invalid'");
      database.executeStatement(
          "insert into inventory_movement values ('sale-invalid-cost', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, 4000, 'invalid-cost')");
      assertRejection(
          database,
          "invalid-cost",
          "Costed sale inventory disposal movement must decrease quantity and carrying cost.");

      database.executeStatement("delete from inventory_movement where posting_id = 'invalid-cost'");
      database.executeStatement(
          "insert into inventory_movement values ('sale-inconsistent', 'inventory', '2026-05-05', 2, 'DISPOSAL', -4, -3000, 'inconsistent')");
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
                    new PostingId(
                        java.util
                            .UUID
                            .nameUUIDFromBytes(
                                ("fingrind-test-postingid:" + postingId)
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .toString()),
                    saleOnCreditWithInventoryRelief()));
    assertEquals(expectedMessage, exception.getMessage());
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
        "insert into inventory_movement values ('purchase', 'inventory', '2026-05-04', 1, 'ACQUISITION', 10, 10000, 'purchase')");
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
