package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import java.util.List;
import java.util.Optional;

/** Loads one foreign-currency obligation for realized-settlement admission and resolution. */
public interface RealizedForeignExchangeLookupStore {
  /**
   * Returns the durable foreign-currency obligation for the selected identifier, when it exists.
   */
  default Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return Optional.empty();
  }

  /**
   * Returns whether an obligation identifier has ever been recorded, including reversed history.
   */
  default boolean hasForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return findForeignCurrencyObligation(foreignCurrencyObligationId).isPresent();
  }

  /** Returns every active foreign-currency obligation for the owned register projection. */
  default List<ForeignCurrencyObligationRecord> foreignCurrencyObligations() {
    return List.of();
  }
}
