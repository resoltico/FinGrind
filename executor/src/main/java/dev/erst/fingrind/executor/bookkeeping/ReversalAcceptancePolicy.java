package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.PostingAccrualCutoffRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.PostingLatvianPayrollRejectionSemantics;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Bookkeeping policy for validating reversal lineage before commit acceptance. */
final class ReversalAcceptancePolicy {
  private ReversalAcceptancePolicy() {}

  /** Returns the first deterministic reversal rejection for the supplied attempt, if any. */
  static Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    return switch (postingRequest.postingLineage()) {
      case PostingLineageModel.Direct _ -> Optional.empty();
      case PostingLineageModel.Reversal reversal -> {
        PostingId priorPostingId = reversal.reference().priorPostingId();
        Optional<CommittedPosting> priorPosting = book.findPosting(priorPostingId);
        if (priorPosting.isEmpty()) {
          yield Optional.of(new BookkeepingPostingRejection.ReversalTargetNotFound(priorPostingId));
        }
        yield reversalRejection(postingRequest.journalEntry(), priorPosting.orElseThrow(), book);
      }
    };
  }

  private static Optional<BookkeepingPostingRejection> reversalRejection(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    PostingId priorPostingId = priorPosting.postingId();
    if (priorPosting.postingLineage().isReversal()) {
      return Optional.of(new ReversalTargetIsReversal(priorPostingId));
    }
    if (book.findReversalFor(priorPostingId).isPresent()) {
      return Optional.of(new BookkeepingPostingRejection.ReversalAlreadyExists(priorPostingId));
    }
    Optional<BookkeepingPostingRejection> accrualCutoffRejection =
        accrualCutoffRejection(candidateReversal, priorPosting, book);
    if (accrualCutoffRejection.isPresent()) {
      return accrualCutoffRejection;
    }
    Optional<BookkeepingPostingRejection> payrollRejection =
        latvianPayrollRejection(candidateReversal, priorPosting, book);
    if (payrollRejection.isPresent()) {
      return payrollRejection;
    }
    if (!negates(candidateReversal, priorPosting.journalEntry())) {
      return Optional.of(
          new BookkeepingPostingRejection.ReversalDoesNotNegateTarget(priorPostingId));
    }
    return Optional.empty();
  }

  private static Optional<BookkeepingPostingRejection> latvianPayrollRejection(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    return priorPosting
        .resolvedOriginatingEntry()
        .map(entry -> latvianPayrollReversalForEntry(entry, candidateReversal, priorPosting, book))
        .orElseGet(Optional::empty);
  }

  private static Optional<BookkeepingPostingRejection> latvianPayrollReversalForEntry(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      JournalEntry candidateReversal,
      CommittedPosting priorPosting,
      PostingValidationStore book) {
    if (entry instanceof LatvianPayrollBookkeepingEntryVariants.NetWageSettlement) {
      return settlementReversalRejection(candidateReversal, priorPosting, book);
    }
    if (entry instanceof LatvianPayrollBookkeepingEntryVariants.StateRemittance) {
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
          entrySemanticsRejection(
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
          entrySemanticsRejection(
              PostingLatvianPayrollRejectionSemantics.runReversalPrecedesRun(
                  run.payrollRunId(), candidateReversal.effectiveDate(), run.effectiveDate())));
    }
    for (LatvianPayrollSettlementKind settlementKind : LatvianPayrollSettlementKind.values()) {
      if (book.findActiveLatvianPayrollSettlement(run.payrollRunId(), settlementKind).isPresent()) {
        return Optional.of(
            entrySemanticsRejection(
                PostingLatvianPayrollRejectionSemantics.runReversalRequiresSettlementsReversed(
                    run.payrollRunId())));
      }
    }
    return Optional.empty();
  }

  private static Optional<BookkeepingPostingRejection> accrualCutoffRejection(
      JournalEntry candidateReversal, CommittedPosting priorPosting, PostingValidationStore book) {
    Optional<AccrualCutoffTarget> target = accrualCutoffTarget(priorPosting);
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
          entrySemanticsRejection(
              PostingAccrualCutoffRejectionSemantics.reversalPrecedesHorizon(
                  BookkeepingEntrySemanticsViolationSupport.CANONICAL_SELECTOR_FIELD,
                  requiredTarget.accrualCutoffId(),
                  candidateReversal.effectiveDate(),
                  cutoff.lifecycleHorizonEffectiveDate())));
    }
    if (requiredTarget.isOrigin() && !cutoff.appliedAmount().isZero()) {
      return Optional.of(
          entrySemanticsRejection(
              PostingAccrualCutoffRejectionSemantics.originReversalRequiresZeroApplications(
                  BookkeepingEntrySemanticsViolationSupport.CANONICAL_SELECTOR_FIELD,
                  requiredTarget.accrualCutoffId())));
    }
    return Optional.empty();
  }

  private static Optional<AccrualCutoffTarget> accrualCutoffTarget(CommittedPosting priorPosting) {
    return priorPosting
        .callerAuthoredEntry()
        .flatMap(
            entry ->
                switch (entry) {
                  case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
                      Optional.of(new AccrualCutoffTarget(prepayment.accrualCutoffId(), true));
                  case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
                      Optional.of(new AccrualCutoffTarget(deferredRevenue.accrualCutoffId(), true));
                  case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
                      Optional.of(new AccrualCutoffTarget(accruedExpense.accrualCutoffId(), true));
                  case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
                      Optional.of(new AccrualCutoffTarget(recognition.accrualCutoffId(), false));
                  case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
                      Optional.of(new AccrualCutoffTarget(settlement.accrualCutoffId(), false));
                  default -> Optional.empty();
                });
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolations entrySemanticsRejection(
      dev.erst.fingrind.contract.bookkeeping.PostingRejection.EntrySemanticsViolation violation) {
    return new BookkeepingPostingRejection.EntrySemanticsViolations(
        java.util.List.of(BookkeepingEntrySemanticsViolationSupport.toLocal(violation)));
  }

  private static boolean negates(JournalEntry candidateReversal, JournalEntry original) {
    return normalizedLines(candidateReversal).equals(negatedLines(original));
  }

  private static Map<LineFingerprint, Long> normalizedLines(JournalEntry journalEntry) {
    return journalEntry.lines().stream()
        .collect(Collectors.groupingBy(LineFingerprint::from, Collectors.counting()));
  }

  private static Map<LineFingerprint, Long> negatedLines(JournalEntry journalEntry) {
    return journalEntry.lines().stream()
        .map(LineFingerprint::negatedFrom)
        .collect(Collectors.groupingBy(fingerprint -> fingerprint, Collectors.counting()));
  }

  /** Canonical fingerprint for one journal line when comparing reversal equivalence. */
  private record LineFingerprint(
      dev.erst.fingrind.core.AccountCode accountCode,
      JournalLine.EntrySide side,
      dev.erst.fingrind.core.PositiveMoney amount) {
    /** Builds a fingerprint that preserves one concrete journal line verbatim. */
    static LineFingerprint from(JournalLine line) {
      return new LineFingerprint(line.accountCode(), line.side(), line.amount());
    }

    /** Builds the fingerprint expected in a full reversal of one journal line. */
    static LineFingerprint negatedFrom(JournalLine line) {
      return new LineFingerprint(line.accountCode(), opposite(line.side()), line.amount());
    }

    private static JournalLine.EntrySide opposite(JournalLine.EntrySide side) {
      return switch (side) {
        case DEBIT -> JournalLine.EntrySide.CREDIT;
        case CREDIT -> JournalLine.EntrySide.DEBIT;
      };
    }
  }

  private record AccrualCutoffTarget(AccrualCutoffId accrualCutoffId, boolean isOrigin) {}
}
