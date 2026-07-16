package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns lifecycle-gated reads of the durable Fixed Assets aggregate registry. */
public final class BookkeepingFixedAssetReadService {
  private final BookkeepingReadStore bookStore;

  /** Creates one fixed-asset register read service for the selected book. */
  public BookkeepingFixedAssetReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
  }

  /** Returns every active fixed asset for the register projection. */
  public BookkeepingReadOutcome<List<FixedAssetRecord>> register(
      Optional<LocalDate> effectiveDateAsOf) {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () -> new BookkeepingReadOutcome.Reported<>(bookStore.fixedAssets(effectiveDateAsOf)));
  }
}
