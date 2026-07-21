package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Integration coverage for reading canonical inventory movements into valuation reports. */
class SqliteInventoryValuationStorageReadTest extends SqliteInventoryCostingFixtureSupport {
  @Test
  void inventoryValuationRead_returnsCanonicalMovementsThroughTheRequestedDate() {
    Path bookPath = tempDirectory.resolve("inventory-valuation-read.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertCanonicalInitializedBookMetadata(database);
          insertInventoryAccount(database, "1400", "Inventory");
          insertPostingFactRow(database, "purchase", "idem-purchase");
          insertPostingFactRow(
              database,
              "sale",
              "idem-sale",
              "2026-04-08",
              "2026-04-08T10:15:30Z",
              "STANDARD",
              PostingOriginKind.SALE_SETTLED);
          insertJournalLineRow(database, "purchase", 0, "1400", "DEBIT", "EUR", 1_000L);
          insertJournalLineRow(database, "purchase", 1, "1400", "CREDIT", "EUR", 1_000L);
          insertJournalLineRow(database, "sale", 0, "1400", "DEBIT", "EUR", 400L);
          insertJournalLineRow(database, "sale", 1, "1400", "CREDIT", "EUR", 400L);
          insertTypedInventoryMovement(
              database,
              "acquisition",
              "1400",
              LocalDate.parse("2026-04-07"),
              InventoryMovementKind.ACQUISITION,
              10L,
              1_000L,
              "purchase");
          insertTypedInventoryMovement(
              database,
              "disposal",
              "1400",
              LocalDate.parse("2026-04-08"),
              InventoryMovementKind.DISPOSAL,
              -4L,
              -400L,
              "sale");
          SqliteInventoryCostingWriter.upsertInventoryOnHand(
              database, new AccountCode("1400"), 6L, 600L, LocalDate.parse("2026-04-08"));
        });

    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqliteReadSession session = SqliteCapabilitySessions.read(store)) {
      assertEquals(
          List.of(
              movement(
                  "purchase", "2026-04-07", 1L, InventoryMovementKind.ACQUISITION, 10L, 1_000L),
              movement("sale", "2026-04-08", 2L, InventoryMovementKind.DISPOSAL, -4L, -400L)),
          session.inventoryValuationMovements(Optional.empty()));
      assertEquals(
          List.of(
              movement(
                  "purchase", "2026-04-07", 1L, InventoryMovementKind.ACQUISITION, 10L, 1_000L)),
          session.inventoryValuationMovements(Optional.of(LocalDate.parse("2026-04-07"))));
    }
  }

  private static InventoryValuationMovementRecord movement(
      String postingId,
      String effectiveDate,
      long sequence,
      InventoryMovementKind kind,
      long quantityDelta,
      long costDeltaMinor) {
    return new InventoryValuationMovementRecord(
        new AccountCode("1400"),
        LocalDate.parse(effectiveDate),
        sequence,
        kind,
        quantityDelta,
        costDeltaMinor,
        new PostingId(java.util.UUID.nameUUIDFromBytes(("fingrind-test-postingid:" + postingId).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString()));
  }
}
