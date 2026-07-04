package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Validates typed bookkeeping-entry contract facts before journal derivation. */
final class BookkeepingEntryValidationSupport {
  /** Foreign-exchange treatment sets accepted by each posting-request route. */
  enum ForeignExchangeAllowance {
    SPOT_SETTLEMENT_ONLY,
    ALL_TREATMENTS
  }

  private BookkeepingEntryValidationSupport() {}

  static LocalDate requireEffectiveDate(LocalDate effectiveDate) {
    return Objects.requireNonNull(effectiveDate, "effectiveDate");
  }

  static AccountCode requireAccountCode(AccountCode accountCode, String fieldName) {
    return Objects.requireNonNull(accountCode, fieldName);
  }

  static MonetaryAmount requirePositiveAmount(MonetaryAmount amount, String fieldName) {
    Objects.requireNonNull(amount, fieldName);
    if (!amount.toMoney().isPositive()) {
      throw new IllegalArgumentException(fieldName + " must carry one positive amount.");
    }
    return amount;
  }

  static void requireTaxSelectionState(
      MonetaryAmount operatorAmount,
      @Nullable TaxSelection taxSelection,
      @Nullable AppliedTax appliedTax,
      TaxApplicationKind... allowedApplicationKinds) {
    Objects.requireNonNull(operatorAmount, "operatorAmount");
    if (taxSelection == null) {
      if (appliedTax != null) {
        throw new IllegalArgumentException("appliedTax requires one matching taxSelection.");
      }
      return;
    }
    Objects.requireNonNull(taxSelection, "taxSelection");
    if (appliedTax == null) {
      return;
    }
    if (!taxSelection.taxRegistrationId().equals(appliedTax.taxRegistrationId())
        || !taxSelection.taxCode().equals(appliedTax.taxCode())) {
      throw new IllegalArgumentException(
          "appliedTax must match the selected taxRegistrationId and taxCode.");
    }
    boolean allowed = false;
    for (TaxApplicationKind allowedApplicationKind : allowedApplicationKinds) {
      if (appliedTax.applicationKind() == allowedApplicationKind) {
        allowed = true;
        break;
      }
    }
    if (!allowed) {
      throw new IllegalArgumentException(
          "appliedTax applicationKind is not supported by this entry kind.");
    }
    requireResolvedOperatorAmount(operatorAmount, appliedTax);
  }

  static void requireTypedEntryForeignExchange(
      MonetaryAmount functionalAmount,
      @Nullable ForeignExchangeDetails foreignExchangeDetails,
      ForeignExchangeAllowance allowance,
      String entryKind) {
    Objects.requireNonNull(functionalAmount, "functionalAmount");
    if (foreignExchangeDetails == null) {
      return;
    }
    requireForeignExchangeTreatment(foreignExchangeDetails, allowance, entryKind);
    if (!functionalAmount.equals(foreignExchangeDetails.functionalAmount())) {
      throw new IllegalArgumentException(
          entryKind + " foreignExchange.functionalAmount must match the entry amount exactly.");
    }
  }

  static void requireDirectJournalForeignExchange(
      JournalEntry journalEntry, @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    Objects.requireNonNull(journalEntry, "journalEntry");
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

  static AppliedTax requireResolvedAppliedTax(@Nullable AppliedTax appliedTax, String entryKind) {
    if (appliedTax == null) {
      throw new IllegalStateException(
          entryKind
              + " tax selection requires executor-owned tax resolution before journalEntry() can be derived.");
    }
    return appliedTax;
  }

  static @Nullable SettlementAdjunct requireOptionalSettlementAdjunct(
      @Nullable SettlementAdjunct settlementAdjunct,
      MonetaryAmount settlementAmount,
      String fieldName) {
    Objects.requireNonNull(settlementAmount, "settlementAmount");
    if (settlementAdjunct == null) {
      return null;
    }
    if (!settlementAmount.currencyCode().equals(settlementAdjunct.amount().currencyCode())) {
      throw new IllegalArgumentException(
          fieldName + ".amount currencyCode must match the entry amount currencyCode.");
    }
    if (settlementAdjunct.amount().toMoney().minorUnits()
        >= settlementAmount.toMoney().minorUnits()) {
      throw new IllegalArgumentException(
          fieldName
              + ".amount must be smaller than the settlement amount so one cash line remains.");
    }
    return settlementAdjunct;
  }

  static @Nullable InventoryRelief requireOptionalInventoryRelief(
      @Nullable InventoryRelief inventoryRelief, MonetaryAmount saleAmount, String fieldName) {
    Objects.requireNonNull(saleAmount, "saleAmount");
    if (inventoryRelief == null) {
      return null;
    }
    if (!saleAmount.currencyCode().equals(inventoryRelief.amount().currencyCode())) {
      throw new IllegalArgumentException(
          fieldName + ".amount currencyCode must match the entry amount currencyCode.");
    }
    return inventoryRelief;
  }

  static List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> requireOpeningBalances(
      List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> balances) {
    List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> requiredBalances =
        List.copyOf(Objects.requireNonNull(balances, "balances"));
    if (requiredBalances.isEmpty()) {
      throw new IllegalArgumentException("Opening position requires at least one opening balance.");
    }
    return requiredBalances;
  }

  static void requireOpeningAccountBalance(
      AccountCode accountCode, JournalLine.EntrySide side, MonetaryAmount amount) {
    Objects.requireNonNull(accountCode, "accountCode");
    Objects.requireNonNull(side, "side");
    requirePositiveAmount(amount, "amount");
  }

  static void requireResolvedReversal(
      LocalDate effectiveDate,
      PostingLineage.Reversal reversal,
      @Nullable JournalEntry resolvedJournalEntry,
      @Nullable ForeignExchangeDetails foreignExchangeDetails) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(reversal, "reversal");
    if (resolvedJournalEntry != null) {
      if (!resolvedJournalEntry.effectiveDate().equals(effectiveDate)) {
        throw new IllegalArgumentException(
            "resolvedJournalEntry effectiveDate must match reversal effectiveDate.");
      }
      requireDirectJournalForeignExchange(resolvedJournalEntry, foreignExchangeDetails);
    }
  }

  static JournalEntry requireResolvedJournalEntry(
      @Nullable JournalEntry resolvedJournalEntry, String entryKind) {
    if (resolvedJournalEntry == null) {
      throw new IllegalStateException(
          entryKind
              + " journalEntry is derived from the referenced posting and becomes available only after executor resolution.");
    }
    return resolvedJournalEntry;
  }

  private static void requireResolvedOperatorAmount(
      MonetaryAmount operatorAmount, AppliedTax appliedTax) {
    String amountCurrency = operatorAmount.currencyCode();
    if (!amountCurrency.equals(appliedTax.taxableAmount().currencyCode())) {
      throw new IllegalArgumentException(
          "appliedTax taxableAmount currencyCode must match the entry amount currencyCode.");
    }
    if (appliedTax.inclusionMode() == TaxInclusionMode.EXCLUSIVE) {
      if (!operatorAmount.equals(appliedTax.taxableAmount())) {
        throw new IllegalArgumentException(
            "Exclusive tax entries must retain the operator-supplied amount as the taxable amount.");
      }
      return;
    }
    if (!operatorAmount.equals(appliedTax.grossAmount())) {
      throw new IllegalArgumentException(
          "Inclusive tax entries must retain the operator-supplied amount as the gross amount.");
    }
  }

  static AccountCode requireTaxAccountCode(AppliedTax appliedTax, String entryKind) {
    if (appliedTax.taxAccountCode() == null) {
      throw new IllegalArgumentException(
          entryKind + " appliedTax must carry taxAccountCode when taxAmount is positive.");
    }
    return appliedTax.taxAccountCode();
  }

  private static void requireForeignExchangeTreatment(
      ForeignExchangeDetails foreignExchangeDetails,
      ForeignExchangeAllowance allowance,
      String entryKind) {
    if (allowance == ForeignExchangeAllowance.SPOT_SETTLEMENT_ONLY
        && foreignExchangeDetails.treatmentKind() != ForeignExchangeTreatmentKind.SPOT_SETTLEMENT) {
      throw new IllegalArgumentException(
          entryKind + " foreignExchange.treatmentKind must be SPOT_SETTLEMENT.");
    }
  }
}
