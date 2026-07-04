package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.BookDoctrineDisplay;
import dev.erst.fingrind.core.BookIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared operator-facing identity rows for one accounting book. */
final class CliBookIdentityDisplay {
  private CliBookIdentityDisplay() {}

  static List<List<String>> detailRows(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Entity", bookIdentity.entityName().value()));
    rows.add(
        List.of(
            "Accounting kernel",
            BookDoctrineDisplay.accountingKernel(
                bookIdentity.bookDoctrine().accountingKernelProfileId())));
    rows.add(
        List.of(
            "Accounting basis",
            BookDoctrineDisplay.accountingBasis(bookIdentity.bookDoctrine().accountingBasis())));
    rows.add(
        List.of(
            "Accounting posture",
            BookDoctrineDisplay.accountingFrameworkPosition(
                bookIdentity.bookDoctrine().accountingFrameworkPosition())));
    rows.add(
        List.of(
            "Entity form",
            BookDoctrineDisplay.entityForm(bookIdentity.bookDoctrine().entityForm())));
    rows.add(
        List.of(
            "Seed template",
            BookDoctrineDisplay.bookTemplate(bookIdentity.bookDoctrine().bookTemplateId())));
    rows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    rows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    return List.copyOf(rows);
  }

  static List<List<String>> contextRows(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return List.of(
        List.of("Entity", bookIdentity.entityName().value()),
        List.of(
            "Seed template",
            BookDoctrineDisplay.bookTemplate(bookIdentity.bookDoctrine().bookTemplateId())),
        List.of(
            "Accounting basis",
            BookDoctrineDisplay.accountingBasis(bookIdentity.bookDoctrine().accountingBasis())),
        List.of("Functional currency", bookIdentity.functionalCurrency().code()),
        List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
  }

  static List<List<String>> rows(BookIdentity bookIdentity) {
    return detailRows(bookIdentity);
  }

  static List<List<String>> summaryRows(BookIdentity bookIdentity) {
    return contextRows(bookIdentity);
  }
}
