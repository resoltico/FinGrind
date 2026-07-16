package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import dev.erst.fingrind.executor.spi.RealizedForeignExchangeLookupStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reconstructs realized-foreign-exchange obligations for in-memory executor tests. */
interface InMemoryRealizedForeignExchangeLookupProjection
    extends RealizedForeignExchangeLookupStore, InMemoryOwnedLifecycleProjectionSource {
  @Override
  default Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    Objects.requireNonNull(foreignCurrencyObligationId, "foreignCurrencyObligationId");
    return foreignCurrencyObligations().stream()
        .filter(
            obligation ->
                obligation.foreignCurrencyObligationId().equals(foreignCurrencyObligationId))
        .findFirst();
  }

  @Override
  default boolean hasForeignCurrencyObligation(
      ForeignCurrencyObligationId foreignCurrencyObligationId) {
    Objects.requireNonNull(foreignCurrencyObligationId, "foreignCurrencyObligationId");
    return InMemoryOwnedLifecycleEntries.historyContains(
        this,
        entry ->
            entry
                    instanceof
                    RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable
                        receivable
                && receivable.foreignCurrencyObligationId().equals(foreignCurrencyObligationId));
  }

  @Override
  default List<ForeignCurrencyObligationRecord> foreignCurrencyObligations() {
    Map<ForeignCurrencyObligationId, ForeignCurrencyObligationRecord> records =
        InMemoryBookSessionSupport.mutableMap();
    InMemoryOwnedLifecycleEntries.activeEntries(this, Optional.empty())
        .forEach(entry -> apply(records, entry));
    return records.values().stream()
        .sorted(
            Comparator.comparing(ForeignCurrencyObligationRecord::originatedOn)
                .thenComparing(record -> record.foreignCurrencyObligationId().value()))
        .toList();
  }

  private static void apply(
      Map<ForeignCurrencyObligationId, ForeignCurrencyObligationRecord> records,
      BookkeepingEntry entry) {
    switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable ->
          records.put(
              receivable.foreignCurrencyObligationId(),
              new ForeignCurrencyObligationRecord(
                  receivable.foreignCurrencyObligationId(),
                  receivable.effectiveDate(),
                  receivable.effectiveDate(),
                  receivable.receivableAccountCode(),
                  receivable.realizedGainAccountCode(),
                  receivable.realizedLossAccountCode(),
                  receivable.foreignExchangeDetails().transactionAmount().toMoney(),
                  receivable.foreignExchangeDetails().functionalAmount().toMoney(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty()));
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement ->
          records.computeIfPresent(
              settlement.foreignCurrencyObligationId(),
              (ignored, obligation) -> {
                var resolved =
                    Objects.requireNonNull(settlement.resolvedSettlement(), "resolvedSettlement");
                return new ForeignCurrencyObligationRecord(
                    obligation.foreignCurrencyObligationId(),
                    obligation.originatedOn(),
                    settlement.effectiveDate(),
                    obligation.receivableAccountCode(),
                    obligation.realizedGainAccountCode(),
                    obligation.realizedLossAccountCode(),
                    obligation.transactionAmount(),
                    obligation.initialFunctionalCarryingAmount(),
                    Optional.of(settlement.effectiveDate()),
                    Optional.of(settlement.foreignExchangeDetails().functionalAmount().toMoney()),
                    Optional.of(resolved.realizedGainOrLossAmount().toMoney()),
                    Optional.of(resolved.gain()));
              });
      default -> {}
    }
  }
}
