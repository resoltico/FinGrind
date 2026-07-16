package dev.erst.fingrind.executor.bookkeeping.read;

import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.List;
import java.util.Objects;

/** Owns lifecycle-gated reads of immutable Latvian payroll runs and settlement lineage. */
public final class BookkeepingLatvianPayrollReadService {
  private final BookkeepingReadStore bookStore;

  /** Creates one payroll-register read service for the selected book. */
  public BookkeepingLatvianPayrollReadService(BookkeepingReadStore bookStore) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
  }

  /** Returns every retained payroll run and settlement row in the store's canonical order. */
  public BookkeepingReadOutcome<PayrollRegisterFacts> register() {
    return BookkeepingReadLifecycleGate.ifInitialized(
        bookStore,
        () ->
            new BookkeepingReadOutcome.Reported<>(
                new PayrollRegisterFacts(
                    bookStore.latvianPayrollRuns(), bookStore.latvianPayrollSettlements())));
  }

  /** Durable payroll facts that must be projected together to retain settlement lineage. */
  public record PayrollRegisterFacts(
      List<LatvianPayrollRunRecord> runs, List<LatvianPayrollSettlementRecord> settlements) {
    /** Copies immutable read facts. */
    public PayrollRegisterFacts {
      runs = List.copyOf(Objects.requireNonNull(runs, "runs"));
      settlements = List.copyOf(Objects.requireNonNull(settlements, "settlements"));
    }
  }
}
