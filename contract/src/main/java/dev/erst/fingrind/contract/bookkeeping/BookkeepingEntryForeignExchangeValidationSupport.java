package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import org.jspecify.annotations.Nullable;

/** Foreign-exchange validation shared by caller-authored bookkeeping entries. */
final class BookkeepingEntryForeignExchangeValidationSupport {
  /** Foreign-exchange treatment sets accepted by each posting-request route. */
  enum ForeignExchangeAllowance {
    SPOT_TRANSACTION_ONLY,
    ALL_TREATMENTS
  }

  private BookkeepingEntryForeignExchangeValidationSupport() {}

  static void requireTypedEntryForeignExchange(
      MonetaryAmount functionalAmount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      ForeignExchangeAllowance allowance,
      String entryKind) {
    java.util.Objects.requireNonNull(functionalAmount, "functionalAmount");
    if (foreignExchangeDetails == null) {
      return;
    }
    requireTypedEntryForeignExchangeTreatment(foreignExchangeDetails, allowance, entryKind);
    if (!functionalAmount.equals(foreignExchangeDetails.functionalAmount())) {
      throw new IllegalArgumentException(
          entryKind + " foreignExchange.functionalAmount must match the entry amount exactly.");
    }
  }

  /** Validates only the treatment facts when the exact functional total is executor-derived. */
  static void requireTypedEntryForeignExchangeTreatment(
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      ForeignExchangeAllowance allowance,
      String entryKind) {
    if (foreignExchangeDetails != null) {
      requireForeignExchangeTreatment(foreignExchangeDetails, allowance, entryKind);
    }
  }

  static void requireDirectJournalForeignExchange(
      JournalEntry journalEntry, @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    java.util.Objects.requireNonNull(journalEntry, "journalEntry");
    if (foreignExchangeDetails == null) {
      return;
    }
    requireForeignExchangeTreatment(
        foreignExchangeDetails, ForeignExchangeAllowance.ALL_TREATMENTS, "directJournal");
    MonetaryAmount journalMagnitude =
        MonetaryAmount.of(
            journalEntry.lines().stream()
                .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
                .map(line -> line.amount().money())
                .reduce(
                    dev.erst.fingrind.core.Money.zero(journalEntry.currencyUnit()),
                    dev.erst.fingrind.core.Money::plus));
    if (!journalMagnitude.equals(foreignExchangeDetails.functionalAmount())) {
      throw new IllegalArgumentException(
          "directJournal foreignExchange.functionalAmount must match the total debit and credit magnitude.");
    }
  }

  private static void requireForeignExchangeTreatment(
      ForeignExchangeDetails foreignExchangeDetails,
      ForeignExchangeAllowance allowance,
      String entryKind) {
    if (allowance == ForeignExchangeAllowance.SPOT_TRANSACTION_ONLY
        && foreignExchangeDetails.treatmentKind()
            != ForeignExchangeTreatmentKind.SPOT_TRANSACTION) {
      throw new IllegalArgumentException(
          entryKind
              + " foreignExchange.treatmentKind must be "
              + ForeignExchangeTreatmentKind.SPOT_TRANSACTION.wireValue()
              + ".");
    }
  }
}
