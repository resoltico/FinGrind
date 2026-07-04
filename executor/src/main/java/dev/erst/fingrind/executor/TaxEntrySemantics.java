package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingTaxSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Entry-semantics owner for optional tax selection on sale and expense business events. */
final class TaxEntrySemantics {
  private TaxEntrySemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      PostingValidationStore book,
      BookkeepingEntry entry,
      String selectorField,
      String selectorValue) {
    Objects.requireNonNull(violations, "violations");
    Objects.requireNonNull(book, "book");
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(selectorField, "selectorField");
    Objects.requireNonNull(selectorValue, "selectorValue");
    switch (entry) {
      case BookkeepingEntry.SaleSettled sale ->
          validateSelection(
              violations,
              book,
              sale.taxSelection(),
              TaxApplicationKind.OUTPUT_SALE,
              selectorField,
              selectorValue);
      case BookkeepingEntry.SaleOnCredit sale ->
          validateSelection(
              violations,
              book,
              sale.taxSelection(),
              TaxApplicationKind.OUTPUT_SALE,
              selectorField,
              selectorValue);
      case BookkeepingEntry.ExpenseSettled expense ->
          validateSelection(
              violations, book, expense.taxSelection(), null, selectorField, selectorValue);
      case BookkeepingEntry.ExpenseOnCredit expense ->
          validateSelection(
              violations, book, expense.taxSelection(), null, selectorField, selectorValue);
      default -> {}
    }
  }

  private static void validateSelection(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      PostingValidationStore book,
      @Nullable TaxSelection taxSelection,
      @Nullable TaxApplicationKind requiredApplicationKind,
      String selectorField,
      String selectorValue) {
    if (taxSelection == null) {
      return;
    }
    DeclaredTaxRegistration registration =
        book.findTaxRegistration(taxSelection.taxRegistrationId()).orElse(null);
    if (registration == null) {
      violations.add(
          BookkeepingTaxSemanticsViolations.unknownTaxRegistration(
              selectorField, selectorValue, taxSelection.taxRegistrationId()));
      return;
    }
    TaxCodeDefinition taxCodeDefinition =
        registration.taxCodes().stream()
            .filter(code -> code.taxCode().equals(taxSelection.taxCode()))
            .findFirst()
            .orElse(null);
    if (taxCodeDefinition == null) {
      violations.add(
          BookkeepingTaxSemanticsViolations.unknownTaxCode(
              selectorField,
              selectorValue,
              taxSelection.taxRegistrationId(),
              taxSelection.taxCode()));
      return;
    }
    if (requiredApplicationKind != null
        && taxCodeDefinition.applicationKind() != requiredApplicationKind) {
      violations.add(
          BookkeepingTaxSemanticsViolations.taxApplicationKindMismatch(
              selectorField,
              selectorValue,
              taxSelection.taxCode(),
              requiredApplicationKind,
              taxCodeDefinition.applicationKind()));
      return;
    }
    if (requiredApplicationKind == null
        && taxCodeDefinition.applicationKind() == TaxApplicationKind.OUTPUT_SALE) {
      violations.add(
          BookkeepingTaxSemanticsViolations.taxApplicationKindMismatch(
              selectorField,
              selectorValue,
              taxSelection.taxCode(),
              TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
              taxCodeDefinition.applicationKind()));
    }
  }
}
