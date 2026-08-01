package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.PostingAccrualCutoffRejectionSemantics;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import java.util.Optional;

/** Accrual Cut-offs-owned reversal admission rules. */
final class ReversalAccrualCutoffAcceptancePolicy {
  private ReversalAccrualCutoffAcceptancePolicy() {}

  static Optional<BookkeepingPostingRejection> rejectionFor(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    Optional<AccrualCutoffTarget> target = targetFor(priorPosting);
    if (target.isEmpty()) {
      return Optional.empty();
    }
    AccrualCutoffTarget requiredTarget = target.orElseThrow();
    AccrualCutoffRecord cutoff =
        book.findAccrualCutoff(requiredTarget.accrualCutoffId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Committed accrual-cutoff posting "
                            + priorPosting.postingId().value()
                            + " has no durable accrual-cutoff aggregate."));
    if (candidateReversal.effectiveDate().isBefore(cutoff.lifecycleHorizonEffectiveDate())) {
      return Optional.of(
          ReversalEntrySemanticsRejectionMapper.toLocal(
              PostingAccrualCutoffRejectionSemantics.reversalPrecedesHorizon(
                  requiredTarget.entryKind(),
                  requiredTarget.accrualCutoffId(),
                  candidateReversal.effectiveDate(),
                  cutoff.lifecycleHorizonEffectiveDate())));
    }
    if (requiredTarget.isOrigin() && !cutoff.appliedAmount().isZero()) {
      return Optional.of(
          ReversalEntrySemanticsRejectionMapper.toLocal(
              PostingAccrualCutoffRejectionSemantics.originReversalRequiresZeroApplications(
                  requiredTarget.entryKind(), requiredTarget.accrualCutoffId())));
    }
    return Optional.empty();
  }

  private static Optional<AccrualCutoffTarget> targetFor(CommittedPosting priorPosting) {
    return priorPosting
        .callerAuthoredEntry()
        .flatMap(
            entry ->
                switch (entry) {
                  case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
                      Optional.of(
                          new AccrualCutoffTarget(
                              prepayment.accrualCutoffId(), true, entry.entryKind()));
                  case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
                      Optional.of(
                          new AccrualCutoffTarget(
                              deferredRevenue.accrualCutoffId(), true, entry.entryKind()));
                  case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
                      Optional.of(
                          new AccrualCutoffTarget(
                              accruedExpense.accrualCutoffId(), true, entry.entryKind()));
                  case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
                      Optional.of(
                          new AccrualCutoffTarget(
                              recognition.accrualCutoffId(), false, entry.entryKind()));
                  case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
                      Optional.of(
                          new AccrualCutoffTarget(
                              settlement.accrualCutoffId(), false, entry.entryKind()));
                  default -> Optional.empty();
                });
  }

  private record AccrualCutoffTarget(
      AccrualCutoffId accrualCutoffId, boolean isOrigin, BookkeepingEntryKind entryKind) {}
}
