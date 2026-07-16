package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns lifecycle-gated reads of the durable accrual cut-off aggregate registry. */
public final class BookkeepingAccrualCutoffReadService {
  private final BookkeepingReadStore bookStore;

  /** Creates one accrual cut-off schedule read service for the selected book. */
  public BookkeepingAccrualCutoffReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
  }

  /** Returns every aggregate projected through the selected inclusive effective-date cutoff. */
  public BookkeepingReadOutcome<List<AccrualCutoffRecord>> schedule(
      Optional<LocalDate> effectiveDateAsOf) {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(bookStore.accrualCutoffs(effectiveDateAsOf)));
  }
}
