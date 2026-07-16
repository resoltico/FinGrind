package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import java.util.List;
import java.util.Optional;

/** Loads one financing aggregate for executor admission and report projection. */
public interface FinancingLookupStore {
  /** Returns the durable financing arrangement for the selected identifier, when it exists. */
  default Optional<FinancingArrangementRecord> findFinancingArrangement(
      FinancingArrangementId financingArrangementId) {
    return Optional.empty();
  }

  /**
   * Returns whether an arrangement identifier has ever been recorded, including reversed history.
   */
  default boolean hasFinancingArrangement(FinancingArrangementId financingArrangementId) {
    return findFinancingArrangement(financingArrangementId).isPresent();
  }

  /** Returns every active financing arrangement for the owned register projection. */
  default List<FinancingArrangementRecord> financingArrangements() {
    return List.of();
  }
}
