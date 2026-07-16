package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.spi.FinancingLookupStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Reconstructs the financing register for in-memory executor tests. */
interface InMemoryFinancingLookupProjection
    extends FinancingLookupStore, InMemoryOwnedLifecycleProjectionSource {
  @Override
  default Optional<FinancingArrangementRecord> findFinancingArrangement(
      FinancingArrangementId financingArrangementId) {
    Objects.requireNonNull(financingArrangementId, "financingArrangementId");
    return financingArrangements().stream()
        .filter(arrangement -> arrangement.financingArrangementId().equals(financingArrangementId))
        .findFirst();
  }

  @Override
  default boolean hasFinancingArrangement(FinancingArrangementId financingArrangementId) {
    Objects.requireNonNull(financingArrangementId, "financingArrangementId");
    return InMemoryOwnedLifecycleEntries.historyContains(
        this,
        entry ->
            entry instanceof FinancingBookkeepingEntryVariants.Borrowing borrowing
                && borrowing.financingArrangementId().equals(financingArrangementId));
  }

  @Override
  default List<FinancingArrangementRecord> financingArrangements() {
    Map<FinancingArrangementId, FinancingArrangementRecord> records =
        InMemoryBookSessionSupport.mutableMap();
    InMemoryOwnedLifecycleEntries.activeEntries(this, Optional.empty())
        .forEach(entry -> apply(records, entry));
    return records.values().stream()
        .sorted(
            Comparator.comparing(FinancingArrangementRecord::originatedOn)
                .thenComparing(record -> record.financingArrangementId().value()))
        .toList();
  }

  private static void apply(
      Map<FinancingArrangementId, FinancingArrangementRecord> records, BookkeepingEntry entry) {
    switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing ->
          records.put(
              borrowing.financingArrangementId(),
              new FinancingArrangementRecord(
                  borrowing.financingArrangementId(),
                  borrowing.effectiveDate(),
                  borrowing.principalLiabilityAccountCode(),
                  borrowing.interestPayableAccountCode(),
                  borrowing.principalAmount().toMoney(),
                  Money.zero(borrowing.principalAmount().toMoney().currencyUnit()),
                  Money.zero(borrowing.principalAmount().toMoney().currencyUnit()),
                  Money.zero(borrowing.principalAmount().toMoney().currencyUnit()),
                  Optional.empty()));
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment ->
          records.computeIfPresent(
              repayment.financingArrangementId(),
              (ignored, arrangement) ->
                  new FinancingArrangementRecord(
                      arrangement.financingArrangementId(),
                      arrangement.originatedOn(),
                      arrangement.principalLiabilityAccountCode(),
                      arrangement.interestPayableAccountCode(),
                      arrangement.originalPrincipal(),
                      arrangement.principalRepaid().plus(repayment.principalAmount().toMoney()),
                      arrangement.interestAccrued(),
                      arrangement.interestPaid(),
                      Optional.of(repayment.effectiveDate())));
      case FinancingBookkeepingEntryVariants.InterestAccrual accrual ->
          records.computeIfPresent(
              accrual.financingArrangementId(),
              (ignored, arrangement) ->
                  new FinancingArrangementRecord(
                      arrangement.financingArrangementId(),
                      arrangement.originatedOn(),
                      arrangement.principalLiabilityAccountCode(),
                      arrangement.interestPayableAccountCode(),
                      arrangement.originalPrincipal(),
                      arrangement.principalRepaid(),
                      arrangement.interestAccrued().plus(accrual.interestAmount().toMoney()),
                      arrangement.interestPaid(),
                      Optional.of(accrual.effectiveDate())));
      case FinancingBookkeepingEntryVariants.InterestPayment payment ->
          records.computeIfPresent(
              payment.financingArrangementId(),
              (ignored, arrangement) ->
                  new FinancingArrangementRecord(
                      arrangement.financingArrangementId(),
                      arrangement.originatedOn(),
                      arrangement.principalLiabilityAccountCode(),
                      arrangement.interestPayableAccountCode(),
                      arrangement.originalPrincipal(),
                      arrangement.principalRepaid(),
                      arrangement.interestAccrued(),
                      arrangement.interestPaid().plus(payment.interestAmount().toMoney()),
                      Optional.of(payment.effectiveDate())));
      default -> {}
    }
  }
}
