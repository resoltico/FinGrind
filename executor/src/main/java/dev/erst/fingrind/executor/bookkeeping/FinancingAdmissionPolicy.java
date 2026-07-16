package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedFinancingApplication;
import java.time.LocalDate;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** First-defense aggregate admission and journal resolution for financing lifecycle events. */
public final class FinancingAdmissionPolicy {
  /** Resolves one financing lifecycle request or returns its first deterministic refusal. */
  public Resolution resolve(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      PostingValidationStore book,
      String selectorValue) {
    return switch (entry) {
      case FinancingBookkeepingEntryVariants.Borrowing borrowing ->
          borrowing(borrowing, book, selectorValue);
      case FinancingBookkeepingEntryVariants.PrincipalRepayment repayment ->
          principalRepayment(repayment, book, selectorValue);
      case FinancingBookkeepingEntryVariants.InterestAccrual interestAccrual ->
          interestAccrual(interestAccrual, book, selectorValue);
      case FinancingBookkeepingEntryVariants.InterestPayment interestPayment ->
          interestPayment(interestPayment, book, selectorValue);
      default -> Resolution.accepted(entry);
    };
  }

  private static Resolution borrowing(
      FinancingBookkeepingEntryVariants.Borrowing borrowing,
      PostingValidationStore book,
      String selectorValue) {
    if (book.hasFinancingArrangement(borrowing.financingArrangementId())) {
      return Resolution.rejected(
          violation(
              "financing-arrangement-id-already-exists",
              "financingArrangementId",
              "entryKind '%s' cannot create financingArrangementId '%s' because that identifier already exists."
                  .formatted(selectorValue, borrowing.financingArrangementId().value())));
    }
    return Resolution.accepted(borrowing);
  }

  private static Resolution principalRepayment(
      FinancingBookkeepingEntryVariants.PrincipalRepayment repayment,
      PostingValidationStore book,
      String selectorValue) {
    Optional<FinancingArrangementRecord> found =
        book.findFinancingArrangement(repayment.financingArrangementId());
    if (found.isEmpty()) {
      return Resolution.rejected(notFound(selectorValue, repayment.financingArrangementId()));
    }
    FinancingArrangementRecord arrangement = found.orElseThrow();
    Optional<BookkeepingPostingRejection.EntrySemanticsViolation> admissibility =
        amountAndHorizon(
            selectorValue,
            repayment.financingArrangementId(),
            repayment.effectiveDate(),
            repayment.principalAmount().toMoney(),
            arrangement.outstandingPrincipal(),
            arrangement.lifecycleHorizon(),
            "principalAmount",
            "financing-principal-repayment-exceeds-outstanding");
    if (admissibility.isPresent()) {
      return Resolution.rejected(admissibility.orElseThrow());
    }
    return Resolution.accepted(
        new FinancingBookkeepingEntryVariants.PrincipalRepayment(
            repayment.effectiveDate(),
            repayment.financingArrangementId(),
            repayment.cashAccountCode(),
            repayment.principalAmount(),
            new ResolvedFinancingApplication(
                arrangement.principalLiabilityAccountCode(),
                arrangement.interestPayableAccountCode())));
  }

  private static Resolution interestAccrual(
      FinancingBookkeepingEntryVariants.InterestAccrual interestAccrual,
      PostingValidationStore book,
      String selectorValue) {
    Optional<FinancingArrangementRecord> found =
        book.findFinancingArrangement(interestAccrual.financingArrangementId());
    if (found.isEmpty()) {
      return Resolution.rejected(notFound(selectorValue, interestAccrual.financingArrangementId()));
    }
    FinancingArrangementRecord arrangement = found.orElseThrow();
    if (interestAccrual.effectiveDate().isBefore(arrangement.lifecycleHorizon())) {
      return Resolution.rejected(
          precedesHorizon(
              selectorValue,
              interestAccrual.financingArrangementId(),
              interestAccrual.effectiveDate(),
              arrangement.lifecycleHorizon()));
    }
    if (!interestAccrual
        .interestAmount()
        .currencyCode()
        .equals(arrangement.originalPrincipal().currencyUnit().code())) {
      return Resolution.rejected(currencyMismatch(selectorValue, "interestAmount", arrangement));
    }
    return Resolution.accepted(
        new FinancingBookkeepingEntryVariants.InterestAccrual(
            interestAccrual.effectiveDate(),
            interestAccrual.financingArrangementId(),
            interestAccrual.interestExpenseAccountCode(),
            interestAccrual.interestAmount(),
            new ResolvedFinancingApplication(
                arrangement.principalLiabilityAccountCode(),
                arrangement.interestPayableAccountCode())));
  }

  private static Resolution interestPayment(
      FinancingBookkeepingEntryVariants.InterestPayment interestPayment,
      PostingValidationStore book,
      String selectorValue) {
    Optional<FinancingArrangementRecord> found =
        book.findFinancingArrangement(interestPayment.financingArrangementId());
    if (found.isEmpty()) {
      return Resolution.rejected(notFound(selectorValue, interestPayment.financingArrangementId()));
    }
    FinancingArrangementRecord arrangement = found.orElseThrow();
    Optional<BookkeepingPostingRejection.EntrySemanticsViolation> admissibility =
        amountAndHorizon(
            selectorValue,
            interestPayment.financingArrangementId(),
            interestPayment.effectiveDate(),
            interestPayment.interestAmount().toMoney(),
            arrangement.outstandingInterest(),
            arrangement.lifecycleHorizon(),
            "interestAmount",
            "financing-interest-payment-exceeds-accrued");
    if (admissibility.isPresent()) {
      return Resolution.rejected(admissibility.orElseThrow());
    }
    return Resolution.accepted(
        new FinancingBookkeepingEntryVariants.InterestPayment(
            interestPayment.effectiveDate(),
            interestPayment.financingArrangementId(),
            interestPayment.cashAccountCode(),
            interestPayment.interestAmount(),
            new ResolvedFinancingApplication(
                arrangement.principalLiabilityAccountCode(),
                arrangement.interestPayableAccountCode())));
  }

  private static Optional<BookkeepingPostingRejection.EntrySemanticsViolation> amountAndHorizon(
      String selectorValue,
      FinancingArrangementId arrangementId,
      LocalDate effectiveDate,
      dev.erst.fingrind.core.Money requested,
      dev.erst.fingrind.core.Money remaining,
      LocalDate horizon,
      String field,
      String amountCode) {
    if (effectiveDate.isBefore(horizon)) {
      return Optional.of(precedesHorizon(selectorValue, arrangementId, effectiveDate, horizon));
    }
    if (!requested.currencyUnit().equals(remaining.currencyUnit())) {
      return Optional.of(
          violation(
              "financing-currency-mismatch",
              field,
              "entryKind '%s' must use the financing arrangement functional currency '%s'."
                  .formatted(selectorValue, remaining.currencyUnit().code())));
    }
    if (requested.compareTo(remaining) > 0) {
      return Optional.of(
          violation(
              amountCode,
              field,
              "entryKind '%s' requests '%s' but financingArrangementId '%s' has only '%s' remaining."
                  .formatted(
                      selectorValue,
                      requested.canonicalDecimal(),
                      arrangementId.value(),
                      remaining.canonicalDecimal())));
    }
    return Optional.empty();
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation notFound(
      String selectorValue, FinancingArrangementId arrangementId) {
    return violation(
        "financing-arrangement-not-found",
        "financingArrangementId",
        "entryKind '%s' cannot find financingArrangementId '%s' in this book."
            .formatted(selectorValue, arrangementId.value()));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation precedesHorizon(
      String selectorValue,
      FinancingArrangementId arrangementId,
      LocalDate effectiveDate,
      LocalDate horizon) {
    return violation(
        "financing-lifecycle-precedes-horizon",
        "effectiveDate",
        "entryKind '%s' uses effectiveDate '%s' before the lifecycle horizon '%s' for financingArrangementId '%s'."
            .formatted(selectorValue, effectiveDate, horizon, arrangementId.value()));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation currencyMismatch(
      String selectorValue, String field, FinancingArrangementRecord arrangement) {
    return violation(
        "financing-currency-mismatch",
        field,
        "entryKind '%s' must use the financing arrangement functional currency '%s'."
            .formatted(selectorValue, arrangement.originalPrincipal().currencyUnit().code()));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation violation(
      String code, String field, String message) {
    return new BookkeepingPostingRejection.EntrySemanticsViolation(code, field, message);
  }

  /** Accepted resolved entry or one deterministic financing rejection. */
  public record Resolution(
      @Nullable BookkeepingEntry entry, Optional<BookkeepingPostingRejection> rejection) {
    public Resolution {
      java.util.Objects.requireNonNull(rejection, "rejection");
      if (rejection.isEmpty()) {
        java.util.Objects.requireNonNull(entry, "entry");
      }
    }

    static Resolution accepted(dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry) {
      return new Resolution(entry, Optional.empty());
    }

    static Resolution rejected(BookkeepingPostingRejection.EntrySemanticsViolation violation) {
      return new Resolution(
          null,
          Optional.of(
              new BookkeepingPostingRejection.EntrySemanticsViolations(
                  java.util.List.of(violation))));
    }
  }
}
