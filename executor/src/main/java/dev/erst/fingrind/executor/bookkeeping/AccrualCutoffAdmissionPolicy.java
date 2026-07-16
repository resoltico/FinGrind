package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingAccrualCutoffRejectionSemantics;
import dev.erst.fingrind.contract.bookkeeping.ResolvedAccrualCutoffApplication;
import dev.erst.fingrind.core.AccrualCutoffApplicationKind;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * First-defense aggregate admission and journal resolution for accrual cut-off lifecycle events.
 */
public final class AccrualCutoffAdmissionPolicy {
  /** Resolves one cut-off lifecycle request or returns its first deterministic refusal. */
  public Resolution resolve(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      PostingValidationStore book,
      String selectorValue) {
    return switch (entry) {
      case AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment ->
          creationResolution(prepayment.accrualCutoffId(), entry, book, selectorValue);
      case AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenue ->
          creationResolution(deferredRevenue.accrualCutoffId(), entry, book, selectorValue);
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpense ->
          creationResolution(accruedExpense.accrualCutoffId(), entry, book, selectorValue);
      case AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition ->
          recognitionResolution(recognition, book, selectorValue);
      case AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement ->
          settlementResolution(settlement, book, selectorValue);
      default -> Resolution.accepted(entry);
    };
  }

  private static Resolution creationResolution(
      AccrualCutoffId accrualCutoffId,
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      PostingValidationStore book,
      String selectorValue) {
    if (book.findAccrualCutoff(accrualCutoffId).isPresent()) {
      return Resolution.rejected(
          PostingAccrualCutoffRejectionSemantics.idAlreadyExists(selectorValue, accrualCutoffId));
    }
    return Resolution.accepted(entry);
  }

  private static Resolution recognitionResolution(
      AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition,
      PostingValidationStore book,
      String selectorValue) {
    Optional<AccrualCutoffRecord> existing = book.findAccrualCutoff(recognition.accrualCutoffId());
    if (existing.isEmpty()) {
      return Resolution.rejected(
          PostingAccrualCutoffRejectionSemantics.notFound(
              selectorValue, recognition.accrualCutoffId()));
    }
    return switch (existing.orElseThrow()) {
      case AccrualCutoffRecord.Prepayment prepayment ->
          recognitionResolution(
              recognition,
              prepayment,
              prepayment.recognitionInterval(),
              prepayment.expenseAccountCode(),
              prepayment.prepaymentAssetAccountCode(),
              selectorValue);
      case AccrualCutoffRecord.DeferredRevenue deferredRevenue ->
          recognitionResolution(
              recognition,
              deferredRevenue,
              deferredRevenue.recognitionInterval(),
              deferredRevenue.deferredRevenueAccountCode(),
              deferredRevenue.revenueAccountCode(),
              selectorValue);
      case AccrualCutoffRecord.AccruedExpense accruedExpense ->
          Resolution.rejected(
              PostingAccrualCutoffRejectionSemantics.applicationKindNotAdmitted(
                  selectorValue, recognition.accrualCutoffId(), accruedExpense.kind()));
    };
  }

  private static Resolution recognitionResolution(
      AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognition,
      AccrualCutoffRecord cutoff,
      dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval interval,
      dev.erst.fingrind.core.AccountCode debitAccountCode,
      dev.erst.fingrind.core.AccountCode creditAccountCode,
      String selectorValue) {
    if (!interval.contains(recognition.effectiveDate())) {
      return Resolution.rejected(
          PostingAccrualCutoffRejectionSemantics.recognitionOutsideInterval(
              selectorValue,
              recognition.accrualCutoffId(),
              recognition.effectiveDate(),
              interval.startDate(),
              interval.endDate()));
    }
    Optional<BookkeepingPostingRejection> horizonRejection =
        horizonRejection(
            selectorValue,
            recognition.accrualCutoffId(),
            recognition.effectiveDate(),
            cutoff.lifecycleHorizonEffectiveDate());
    if (horizonRejection.isPresent()) {
      return Resolution.rejected(horizonRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> amountRejection =
        amountRejection(
            selectorValue,
            recognition.accrualCutoffId(),
            recognition.amount(),
            cutoff.remainingAmount());
    if (amountRejection.isPresent()) {
      return Resolution.rejected(amountRejection.orElseThrow());
    }
    return Resolution.accepted(
        new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
            recognition.effectiveDate(),
            recognition.accrualCutoffId(),
            recognition.amount(),
            new ResolvedAccrualCutoffApplication(
                cutoff.kind(),
                AccrualCutoffApplicationKind.RECOGNITION,
                debitAccountCode,
                creditAccountCode)));
  }

  private static Resolution settlementResolution(
      AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlement,
      PostingValidationStore book,
      String selectorValue) {
    Optional<AccrualCutoffRecord> existing = book.findAccrualCutoff(settlement.accrualCutoffId());
    if (existing.isEmpty()) {
      return Resolution.rejected(
          PostingAccrualCutoffRejectionSemantics.notFound(
              selectorValue, settlement.accrualCutoffId()));
    }
    if (!(existing.orElseThrow() instanceof AccrualCutoffRecord.AccruedExpense accruedExpense)) {
      return Resolution.rejected(
          PostingAccrualCutoffRejectionSemantics.applicationKindNotAdmitted(
              selectorValue, settlement.accrualCutoffId(), existing.orElseThrow().kind()));
    }
    Optional<BookkeepingPostingRejection> horizonRejection =
        horizonRejection(
            selectorValue,
            settlement.accrualCutoffId(),
            settlement.effectiveDate(),
            accruedExpense.lifecycleHorizonEffectiveDate());
    if (horizonRejection.isPresent()) {
      return Resolution.rejected(horizonRejection.orElseThrow());
    }
    Optional<BookkeepingPostingRejection> amountRejection =
        amountRejection(
            selectorValue,
            settlement.accrualCutoffId(),
            settlement.amount(),
            accruedExpense.remainingAmount());
    if (amountRejection.isPresent()) {
      return Resolution.rejected(amountRejection.orElseThrow());
    }
    return Resolution.accepted(
        new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
            settlement.effectiveDate(),
            settlement.accrualCutoffId(),
            settlement.cashAccountCode(),
            settlement.amount(),
            new ResolvedAccrualCutoffApplication(
                accruedExpense.kind(),
                AccrualCutoffApplicationKind.SETTLEMENT,
                accruedExpense.accruedExpenseLiabilityAccountCode(),
                settlement.cashAccountCode())));
  }

  private static Optional<BookkeepingPostingRejection> horizonRejection(
      String selectorValue,
      AccrualCutoffId accrualCutoffId,
      LocalDate effectiveDate,
      LocalDate horizonEffectiveDate) {
    if (!effectiveDate.isBefore(horizonEffectiveDate)) {
      return Optional.empty();
    }
    return Optional.of(
        entrySemanticsRejection(
            PostingAccrualCutoffRejectionSemantics.applicationPrecedesHorizon(
                selectorValue, accrualCutoffId, effectiveDate, horizonEffectiveDate)));
  }

  private static Optional<BookkeepingPostingRejection> amountRejection(
      String selectorValue,
      AccrualCutoffId accrualCutoffId,
      MonetaryAmount requestedAmount,
      Money remainingAmount) {
    Money requestedMoney = requestedAmount.toMoney();
    if (!requestedMoney.currencyUnit().equals(remainingAmount.currencyUnit())) {
      return Optional.of(
          new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
              remainingAmount.currencyUnit(), requestedMoney.currencyUnit()));
    }
    if (requestedMoney.compareTo(remainingAmount) <= 0) {
      return Optional.empty();
    }
    return Optional.of(
        entrySemanticsRejection(
            PostingAccrualCutoffRejectionSemantics.applicationExceedsRemainingAmount(
                selectorValue,
                accrualCutoffId,
                requestedAmount,
                MonetaryAmount.of(remainingAmount))));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolations entrySemanticsRejection(
      dev.erst.fingrind.contract.bookkeeping.PostingRejection.EntrySemanticsViolation violation) {
    return new BookkeepingPostingRejection.EntrySemanticsViolations(
        List.of(BookkeepingEntrySemanticsViolationSupport.toLocal(violation)));
  }

  /** One resolved entry or its deterministic entry-semantics rejection. */
  public record Resolution(
      @Nullable BookkeepingEntry entry, Optional<BookkeepingPostingRejection> rejection) {
    public Resolution {
      rejection =
          Optional.ofNullable(
              java.util.Objects.requireNonNull(rejection, "rejection").orElse(null));
      if (rejection.isEmpty() && entry == null) {
        throw new IllegalArgumentException(
            "Accepted accrual cut-off resolution requires one entry.");
      }
    }

    static Resolution accepted(BookkeepingEntry entry) {
      return new Resolution(entry, Optional.empty());
    }

    static Resolution rejected(
        dev.erst.fingrind.contract.bookkeeping.PostingRejection.EntrySemanticsViolation rejection) {
      return rejected(entrySemanticsRejection(rejection));
    }

    static Resolution rejected(BookkeepingPostingRejection rejection) {
      return new Resolution(
          null, Optional.of(java.util.Objects.requireNonNull(rejection, "rejection")));
    }
  }
}
