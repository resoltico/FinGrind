package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Loads one durable accrual cut-off aggregate for executor admission and resolution. */
public interface AccrualCutoffLookupStore {
  /** Returns the durable cut-off aggregate for the selected identifier, when it exists. */
  default Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
    return Optional.empty();
  }

  /** Returns durable cut-off aggregates projected through the selected inclusive effective date. */
  default List<AccrualCutoffRecord> accrualCutoffs(Optional<LocalDate> effectiveDateAsOf) {
    return List.of();
  }
}
