package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;

/** Owns lifecycle-gated reads of the durable Financing aggregate registry. */
public final class BookkeepingFinancingReadService {
  private final BookkeepingReadStore bookStore;

  /** Creates one financing register read service for the selected book. */
  public BookkeepingFinancingReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
  }

  /** Returns every active financing arrangement for the register projection. */
  public BookkeepingReadOutcome<List<FinancingArrangementRecord>> register() {
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore, () -> new BookkeepingReadOutcome.Reported<>(bookStore.financingArrangements()));
  }
}
