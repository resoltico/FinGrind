package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedRealizedForeignExchangeSettlement;
import dev.erst.fingrind.core.Money;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** First-defense aggregate admission and journal resolution for realized FX settlement events. */
public final class RealizedForeignExchangeAdmissionPolicy {
  /** Resolves one realized-FX lifecycle request or returns its first deterministic refusal. */
  public Resolution resolve(
      dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry entry,
      PostingValidationStore book,
      String selectorValue) {
    return switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable ->
          receivable(receivable, book, selectorValue);
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement ->
          settlement(settlement, book, selectorValue);
      default -> Resolution.accepted(entry);
    };
  }

  private static Resolution receivable(
      RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable,
      PostingValidationStore book,
      String selectorValue) {
    if (book.hasForeignCurrencyObligation(receivable.foreignCurrencyObligationId())) {
      return Resolution.rejected(
          violation(
              "foreign-currency-obligation-id-already-exists",
              "foreignCurrencyObligationId",
              "entryKind '%s' cannot create foreignCurrencyObligationId '%s' because that identifier already exists."
                  .formatted(selectorValue, receivable.foreignCurrencyObligationId().value())));
    }
    return Resolution.accepted(receivable);
  }

  private static Resolution settlement(
      RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement,
      PostingValidationStore book,
      String selectorValue) {
    Optional<ForeignCurrencyObligationRecord> found =
        book.findForeignCurrencyObligation(settlement.foreignCurrencyObligationId());
    if (found.isEmpty()) {
      return Resolution.rejected(notFound(selectorValue, settlement.foreignCurrencyObligationId()));
    }
    ForeignCurrencyObligationRecord obligation = found.orElseThrow();
    if (!obligation.unsettled()) {
      return Resolution.rejected(
          violation(
              "foreign-currency-obligation-already-settled",
              "foreignCurrencyObligationId",
              "entryKind '%s' cannot settle foreignCurrencyObligationId '%s' because it is already settled."
                  .formatted(selectorValue, settlement.foreignCurrencyObligationId().value())));
    }
    if (settlement.effectiveDate().isBefore(obligation.lifecycleHorizon())) {
      return Resolution.rejected(
          violation(
              "realized-foreign-exchange-settlement-precedes-lifecycle-horizon",
              "effectiveDate",
              "entryKind '%s' uses effectiveDate '%s' before the lifecycle horizon '%s' for foreignCurrencyObligationId '%s'."
                  .formatted(
                      selectorValue,
                      settlement.effectiveDate(),
                      obligation.lifecycleHorizon(),
                      settlement.foreignCurrencyObligationId().value())));
    }
    if (!settlement
        .foreignExchangeDetails()
        .transactionAmount()
        .toMoney()
        .equals(obligation.transactionAmount())) {
      return Resolution.rejected(
          violation(
              "realized-foreign-exchange-settlement-transaction-amount-mismatch",
              "foreignExchange.transactionAmount",
              "entryKind '%s' must settle the exact transaction amount '%s' for foreignCurrencyObligationId '%s'."
                  .formatted(
                      selectorValue,
                      obligation.transactionAmount().canonicalDecimal(),
                      settlement.foreignCurrencyObligationId().value())));
    }
    Money functionalSettlement = settlement.foreignExchangeDetails().functionalAmount().toMoney();
    Money carrying = obligation.initialFunctionalCarryingAmount();
    if (!functionalSettlement.currencyUnit().equals(carrying.currencyUnit())) {
      return Resolution.rejected(
          violation(
              "realized-foreign-exchange-settlement-functional-currency-mismatch",
              "foreignExchange.functionalAmount",
              "entryKind '%s' must use functional currency '%s' for foreignCurrencyObligationId '%s'."
                  .formatted(
                      selectorValue,
                      carrying.currencyUnit().code(),
                      settlement.foreignCurrencyObligationId().value())));
    }
    Money difference =
        Money.ofMinorUnits(
            carrying.currencyUnit(),
            Math.abs(functionalSettlement.minorUnits() - carrying.minorUnits()));
    boolean gain = functionalSettlement.compareTo(carrying) >= 0;
    return Resolution.accepted(
        new RealizedForeignExchangeBookkeepingEntryVariants.Settlement(
            settlement.effectiveDate(),
            settlement.foreignCurrencyObligationId(),
            settlement.cashAccountCode(),
            settlement.foreignExchangeDetails(),
            new ResolvedRealizedForeignExchangeSettlement(
                obligation.receivableAccountCode(),
                gain ? obligation.realizedGainAccountCode() : obligation.realizedLossAccountCode(),
                dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(carrying),
                dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(difference),
                gain)));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation notFound(
      String selectorValue, ForeignCurrencyObligationId obligationId) {
    return violation(
        "foreign-currency-obligation-not-found",
        "foreignCurrencyObligationId",
        "entryKind '%s' cannot find foreignCurrencyObligationId '%s' in this book."
            .formatted(selectorValue, obligationId.value()));
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation violation(
      String code, String field, String message) {
    return new BookkeepingPostingRejection.EntrySemanticsViolation(code, field, message);
  }

  /** Accepted resolved entry or one deterministic realized-FX rejection. */
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
