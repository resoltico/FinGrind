package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.PostingFinancingRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.PostingFixedAssetRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.PostingRealizedForeignExchangeRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Optional;

/** Fixed Assets, Financing, and Realized FX reversal admission rules. */
final class ReversalLifecycleAcceptancePolicy {
  private ReversalLifecycleAcceptancePolicy() {}

  static Optional<BookkeepingPostingRejection> rejectionFor(
      CommittedPosting priorPosting, PostingValidationStore book) {
    Optional<BookkeepingPostingRejection> fixedAssetRejection =
        fixedAssetRejection(priorPosting, book);
    if (fixedAssetRejection.isPresent()) {
      return fixedAssetRejection;
    }
    Optional<BookkeepingPostingRejection> financingRejection =
        financingRejection(priorPosting, book);
    if (financingRejection.isPresent()) {
      return financingRejection;
    }
    return foreignExchangeRejection(priorPosting, book);
  }

  private static Optional<BookkeepingPostingRejection> fixedAssetRejection(
      CommittedPosting priorPosting, PostingValidationStore book) {
    return fixedAssetCapitalizationTarget(priorPosting)
        .flatMap(
            target -> {
              FixedAssetRecord asset =
                  book.findFixedAsset(target.fixedAssetId())
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Committed fixed-asset capitalization "
                                      + priorPosting.postingId().value()
                                      + " has no durable fixed-asset aggregate."));
              if (asset.depreciationPeriodsApplied() == 0 && asset.disposedOn().isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(
                  ReversalEntrySemanticsRejectionMapper.toLocal(
                      PostingFixedAssetRejectionSemantics
                          .capitalizationReversalRequiresApplicationsReversed(
                              target.entryKind(), target.fixedAssetId())));
            });
  }

  private static Optional<BookkeepingPostingRejection> financingRejection(
      CommittedPosting priorPosting, PostingValidationStore book) {
    return financingBorrowingTarget(priorPosting)
        .flatMap(
            target -> {
              FinancingArrangementRecord arrangement =
                  book.findFinancingArrangement(target.financingArrangementId())
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Committed financing borrowing "
                                      + priorPosting.postingId().value()
                                      + " has no durable financing aggregate."));
              if (arrangement.principalRepaid().isZero()
                  && arrangement.interestAccrued().isZero()) {
                return Optional.empty();
              }
              return Optional.of(
                  ReversalEntrySemanticsRejectionMapper.toLocal(
                      PostingFinancingRejectionSemantics
                          .borrowingReversalRequiresApplicationsReversed(
                              target.entryKind(), target.financingArrangementId())));
            });
  }

  private static Optional<BookkeepingPostingRejection> foreignExchangeRejection(
      CommittedPosting priorPosting, PostingValidationStore book) {
    return foreignCurrencyObligationTarget(priorPosting)
        .flatMap(
            target -> {
              ForeignCurrencyObligationRecord obligation =
                  book.findForeignCurrencyObligation(target.foreignCurrencyObligationId())
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "Committed foreign-currency obligation "
                                      + priorPosting.postingId().value()
                                      + " has no durable foreign-currency obligation aggregate."));
              if (obligation.unsettled()) {
                return Optional.empty();
              }
              return Optional.of(
                  ReversalEntrySemanticsRejectionMapper.toLocal(
                      PostingRealizedForeignExchangeRejectionSemantics
                          .obligationReversalRequiresSettlementReversed(
                              target.entryKind(), target.foreignCurrencyObligationId())));
            });
  }

  private static Optional<FixedAssetCapitalizationTarget> fixedAssetCapitalizationTarget(
      CommittedPosting priorPosting) {
    return priorPosting
        .callerAuthoredEntry()
        .flatMap(
            entry ->
                entry instanceof FixedAssetBookkeepingEntryVariants.Capitalization capitalization
                    ? Optional.of(
                        new FixedAssetCapitalizationTarget(
                            capitalization.fixedAssetId(), entry.entryKind()))
                    : Optional.empty());
  }

  private static Optional<FinancingBorrowingTarget> financingBorrowingTarget(
      CommittedPosting priorPosting) {
    return priorPosting
        .callerAuthoredEntry()
        .flatMap(
            entry ->
                entry instanceof FinancingBookkeepingEntryVariants.Borrowing borrowing
                    ? Optional.of(
                        new FinancingBorrowingTarget(
                            borrowing.financingArrangementId(), entry.entryKind()))
                    : Optional.empty());
  }

  private static Optional<ForeignCurrencyObligationTarget> foreignCurrencyObligationTarget(
      CommittedPosting priorPosting) {
    return priorPosting
        .callerAuthoredEntry()
        .flatMap(
            entry ->
                entry
                        instanceof
                        RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable
                            receivable
                    ? Optional.of(
                        new ForeignCurrencyObligationTarget(
                            receivable.foreignCurrencyObligationId(), entry.entryKind()))
                    : Optional.empty());
  }

  private record FixedAssetCapitalizationTarget(
      FixedAssetId fixedAssetId, BookkeepingEntryKind entryKind) {}

  private record FinancingBorrowingTarget(
      FinancingArrangementId financingArrangementId, BookkeepingEntryKind entryKind) {}

  private record ForeignCurrencyObligationTarget(
      ForeignCurrencyObligationId foreignCurrencyObligationId, BookkeepingEntryKind entryKind) {}
}
