package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.InventoryValuationCriteria;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationView;
import dev.erst.fingrind.executor.bookkeeping.reporting.BookkeepingReportingService;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;

/** Local read service that owns inventory-ledger valuation lifecycle gating and replay. */
public final class BookkeepingInventoryReadService {
  private final BookkeepingReadStore bookStore;
  private final BookkeepingReportingService reportingService;

  /** Creates the inventory valuation service for one selected-book read store. */
  public BookkeepingInventoryReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.reportingService = new BookkeepingReportingService(this.bookStore);
  }

  /** Replays the exact inventory movement ledger for one point-in-time valuation. */
  public BookkeepingReadOutcome<List<InventoryValuationView>> inventoryValuation(
      InventoryValuationCriteria query) {
    Objects.requireNonNull(query, "query");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(reportingService.inventoryValuation(query)));
  }
}
