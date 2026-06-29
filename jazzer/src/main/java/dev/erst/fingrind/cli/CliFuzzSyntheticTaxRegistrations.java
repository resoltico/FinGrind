package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.spi.TaxRegistrationLookupStore;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Synthetic declared-tax-registration fixtures shared by Jazzer request helpers. */
final class CliFuzzSyntheticTaxRegistrations {
  private static final TaxRegistrationLookupStore EMPTY_LOOKUP_STORE =
      taxRegistrationId -> Optional.empty();

  private CliFuzzSyntheticTaxRegistrations() {}

  static TaxRegistrationLookupStore lookupStore(BookkeepingEntry entry, Instant declaredAt) {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(declaredAt, "declaredAt");
    return switch (entry) {
      case BookkeepingEntry.Sale sale ->
          lookupStore(
              sale.taxSelection(), sale.appliedTax(), TaxApplicationKind.OUTPUT_SALE, declaredAt);
      case BookkeepingEntry.Expense expense ->
          lookupStore(
              expense.taxSelection(),
              expense.appliedTax(),
              inferredExpenseApplicationKind(expense),
              declaredAt);
      default -> EMPTY_LOOKUP_STORE;
    };
  }

  private static TaxRegistrationLookupStore lookupStore(
      @Nullable TaxSelection selection,
      @Nullable AppliedTax appliedTax,
      TaxApplicationKind fallbackApplicationKind,
      Instant declaredAt) {
    if (selection == null) {
      return EMPTY_LOOKUP_STORE;
    }
    DeclaredTaxRegistration registration =
        appliedTax == null
            ? declaredTaxRegistration(selection, fallbackApplicationKind, declaredAt)
            : declaredTaxRegistration(selection, appliedTax, declaredAt);
    return taxRegistrationId ->
        registration.taxRegistrationId().equals(taxRegistrationId)
            ? Optional.of(registration)
            : Optional.empty();
  }

  private static TaxApplicationKind inferredExpenseApplicationKind(
      BookkeepingEntry.Expense expense) {
    if (expense.appliedTax() != null) {
      return expense.appliedTax().applicationKind();
    }
    if (expense.taxSelection() == null) {
      return TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE;
    }
    return expense.taxSelection().taxCode().value().contains("nonrecoverable")
        ? TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE
        : TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE;
  }

  private static DeclaredTaxRegistration declaredTaxRegistration(
      TaxSelection selection, AppliedTax appliedTax, Instant declaredAt) {
    return new DeclaredTaxRegistration(
        selection.taxRegistrationId(),
        new TaxRegistrationName("Synthetic " + selection.taxRegistrationId().value()),
        new TaxJurisdiction("LV"),
        null,
        payableAccountCode(appliedTax),
        recoverableAccountCode(appliedTax),
        TaxObligationFrequency.MONTHLY,
        20,
        java.util.List.of(
            new TaxCodeDefinition(
                selection.taxCode(),
                appliedTax.taxCodeName(),
                appliedTax.rate(),
                appliedTax.inclusionMode(),
                appliedTax.applicationKind())),
        declaredAt);
  }

  private static DeclaredTaxRegistration declaredTaxRegistration(
      TaxSelection selection, TaxApplicationKind applicationKind, Instant declaredAt) {
    return new DeclaredTaxRegistration(
        selection.taxRegistrationId(),
        new TaxRegistrationName("Synthetic " + selection.taxRegistrationId().value()),
        new TaxJurisdiction("LV"),
        null,
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        java.util.List.of(taxCodeDefinition(selection, applicationKind)),
        declaredAt);
  }

  private static TaxCodeDefinition taxCodeDefinition(
      TaxSelection selection, TaxApplicationKind applicationKind) {
    return switch (applicationKind) {
      case OUTPUT_SALE ->
          new TaxCodeDefinition(
              selection.taxCode(),
              new TaxCodeName("Synthetic " + selection.taxCode().value()),
              new TaxRate(210_000),
              TaxInclusionMode.EXCLUSIVE,
              applicationKind);
      case INPUT_EXPENSE_RECOVERABLE ->
          new TaxCodeDefinition(
              selection.taxCode(),
              new TaxCodeName("Synthetic " + selection.taxCode().value()),
              new TaxRate(210_000),
              TaxInclusionMode.INCLUSIVE,
              applicationKind);
      case INPUT_EXPENSE_NONRECOVERABLE ->
          new TaxCodeDefinition(
              selection.taxCode(),
              new TaxCodeName("Synthetic " + selection.taxCode().value()),
              new TaxRate(120_000),
              TaxInclusionMode.INCLUSIVE,
              applicationKind);
    };
  }

  private static AccountCode payableAccountCode(AppliedTax appliedTax) {
    if (appliedTax.applicationKind() == TaxApplicationKind.OUTPUT_SALE
        && appliedTax.taxAccountCode() != null) {
      return appliedTax.taxAccountCode();
    }
    return new AccountCode("2100");
  }

  private static AccountCode recoverableAccountCode(AppliedTax appliedTax) {
    if (appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE
        && appliedTax.taxAccountCode() != null) {
      return appliedTax.taxAccountCode();
    }
    return new AccountCode("1300");
  }
}
