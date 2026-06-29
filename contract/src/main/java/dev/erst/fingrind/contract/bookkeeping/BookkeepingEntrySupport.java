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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared validation and journal-derivation support for typed bookkeeping entry variants. */
final class BookkeepingEntrySupport {
  /** Foreign-exchange treatment sets accepted by each posting-request route. */
  enum ForeignExchangeAllowance {
    SPOT_SETTLEMENT_ONLY,
    ALL_TREATMENTS
  }

  private BookkeepingEntrySupport() {}

  static JournalEntry pairedEntry(
      LocalDate effectiveDate,
      AccountCode debitAccountCode,
      AccountCode creditAccountCode,
      MonetaryAmount amount) {
    return new JournalEntry(
        requireEffectiveDate(effectiveDate),
        List.of(
            new JournalLine(debitAccountCode, JournalLine.EntrySide.DEBIT, amount.toMoney()),
            new JournalLine(creditAccountCode, JournalLine.EntrySide.CREDIT, amount.toMoney())));
  }

  static JournalEntry saleEntry(
      LocalDate effectiveDate,
      AccountCode cashAccountCode,
      AccountCode revenueAccountCode,
      MonetaryAmount operatorAmount,
      AppliedTax appliedTax) {
    if (appliedTax.applicationKind() != TaxApplicationKind.OUTPUT_SALE) {
      throw new IllegalArgumentException("Resolved sale tax must use applicationKind OUTPUT_SALE.");
    }
    List<JournalLine> lines = new ArrayList<>();
    lines.add(
        new JournalLine(
            cashAccountCode, JournalLine.EntrySide.DEBIT, appliedTax.grossAmount().toMoney()));
    lines.add(
        new JournalLine(
            revenueAccountCode,
            JournalLine.EntrySide.CREDIT,
            appliedTax.taxableAmount().toMoney()));
    if (appliedTax.taxAmount().toMoney().isPositive()) {
      lines.add(
          new JournalLine(
              requireTaxAccountCode(appliedTax, "sale"),
              JournalLine.EntrySide.CREDIT,
              appliedTax.taxAmount().toMoney()));
    }
    requireResolvedOperatorAmount(operatorAmount, appliedTax);
    return new JournalEntry(effectiveDate, lines);
  }

  static JournalEntry expenseEntry(
      LocalDate effectiveDate,
      AccountCode expenseAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount operatorAmount,
      AppliedTax appliedTax) {
    List<JournalLine> lines = new ArrayList<>();
    if (appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE) {
      lines.add(
          new JournalLine(
              expenseAccountCode,
              JournalLine.EntrySide.DEBIT,
              appliedTax.taxableAmount().toMoney()));
      if (appliedTax.taxAmount().toMoney().isPositive()) {
        lines.add(
            new JournalLine(
                requireTaxAccountCode(appliedTax, "expense"),
                JournalLine.EntrySide.DEBIT,
                appliedTax.taxAmount().toMoney()));
      }
      lines.add(
          new JournalLine(
              cashAccountCode, JournalLine.EntrySide.CREDIT, appliedTax.grossAmount().toMoney()));
    } else if (appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE) {
      lines.add(
          new JournalLine(
              expenseAccountCode, JournalLine.EntrySide.DEBIT, appliedTax.grossAmount().toMoney()));
      lines.add(
          new JournalLine(
              cashAccountCode, JournalLine.EntrySide.CREDIT, appliedTax.grossAmount().toMoney()));
    } else {
      throw new IllegalArgumentException(
          "Resolved expense tax cannot use applicationKind OUTPUT_SALE.");
    }
    requireResolvedOperatorAmount(operatorAmount, appliedTax);
    return new JournalEntry(effectiveDate, lines);
  }

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

  private static AccountCode requireTaxAccountCode(AppliedTax appliedTax, String entryKind) {
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
