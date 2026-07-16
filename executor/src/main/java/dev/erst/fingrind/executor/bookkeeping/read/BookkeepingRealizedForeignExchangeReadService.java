package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;

/** Owns lifecycle-gated reads of the durable Realized Foreign Exchange aggregate registry. */
public final class BookkeepingRealizedForeignExchangeReadService {
  private final BookkeepingReadStore bookStore;

  /** Creates one realized foreign-exchange register read service for the selected book. */
  public BookkeepingRealizedForeignExchangeReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
  }

  /** Returns every active foreign-currency obligation for the register projection. */
  public BookkeepingReadOutcome<List<ForeignCurrencyObligationRecord>> register() {
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(bookStore.foreignCurrencyObligations()));
  }
}
