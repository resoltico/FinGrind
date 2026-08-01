package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.PostingLatvianPayrollRejectionSemantics;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.JournalEntry;
import java.util.Optional;

/** Latvian Payroll-owned reversal admission rules. */
final class ReversalLatvianPayrollAcceptancePolicy {
  private ReversalLatvianPayrollAcceptancePolicy() {}

  static Optional<BookkeepingPostingRejection> rejectionFor(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    return priorPosting
        .resolvedOriginatingEntry()
        .map(entry -> rejectionForEntry(entry, candidateReversal, priorPosting, book))
        .orElseGet(Optional::empty);
  }

  private static Optional<BookkeepingPostingRejection> rejectionForEntry(
      BookkeepingEntry entry,
      JournalEntry candidateReversal,
      CommittedPosting priorPosting,
      PostingValidationStore book) {
    if (entry instanceof LatvianPayrollBookkeepingEntryVariants.NetWageSettlement
        || entry instanceof LatvianPayrollBookkeepingEntryVariants.StateRemittance) {
      return settlementReversalRejection(candidateReversal, priorPosting, book);
    }
    if (entry instanceof LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll) {
      return runReversalRejection(candidateReversal, priorPosting, book);
    }
    return Optional.empty();
  }

  private static Optional<BookkeepingPostingRejection> settlementReversalRejection(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    LatvianPayrollSettlementRecord settlement =
        book.findLatvianPayrollSettlementByPosting(priorPosting.postingId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Committed Latvian payroll settlement "
                            + priorPosting.postingId().value()
                            + " has no durable settlement record."));
    if (candidateReversal.effectiveDate().isBefore(settlement.effectiveDate())) {
      return Optional.of(
          ReversalEntrySemanticsRejectionMapper.toLocal(
              PostingLatvianPayrollRejectionSemantics.settlementReversalPrecedesSettlement(
                  settlement.payrollRunId(),
                  candidateReversal.effectiveDate(),
                  settlement.effectiveDate())));
    }
    return Optional.empty();
  }

  private static Optional<BookkeepingPostingRejection> runReversalRejection(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    LatvianPayrollRunRecord run =
        book.findLatvianPayrollRunByOriginPosting(priorPosting.postingId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Committed Latvian payroll run "
                            + priorPosting.postingId().value()
                            + " has no durable run record."));
    if (candidateReversal.effectiveDate().isBefore(run.effectiveDate())) {
      return Optional.of(
          ReversalEntrySemanticsRejectionMapper.toLocal(
              PostingLatvianPayrollRejectionSemantics.runReversalPrecedesRun(
                  run.payrollRunId(), candidateReversal.effectiveDate(), run.effectiveDate())));
    }
    for (LatvianPayrollSettlementKind settlementKind : LatvianPayrollSettlementKind.values()) {
      if (book.findActiveLatvianPayrollSettlement(run.payrollRunId(), settlementKind).isPresent()) {
        return Optional.of(
            ReversalEntrySemanticsRejectionMapper.toLocal(
                PostingLatvianPayrollRejectionSemantics.runReversalRequiresSettlementsReversed(
                    run.payrollRunId())));
      }
    }
    return Optional.empty();
  }
}
