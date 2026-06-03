package dev.erst.fingrind.cli;

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
            "Accounting kernel", bookIdentity.bookDoctrine().accountingKernelProfileId().value()));
    rows.add(
        List.of("Accounting basis", bookIdentity.bookDoctrine().accountingBasis().wireValue()));
    rows.add(
        List.of(
            "Framework posture",
            bookIdentity.bookDoctrine().accountingFrameworkPosition().wireValue()));
    rows.add(List.of("Entity form", bookIdentity.bookDoctrine().entityForm().wireValue()));
    rows.add(List.of("Book template", bookIdentity.bookDoctrine().bookTemplateId().wireValue()));
    rows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    rows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    return List.copyOf(rows);
  }

  static List<List<String>> rows(BookIdentity bookIdentity) {
    return detailRows(bookIdentity);
  }

  static List<List<String>> summaryRows(BookIdentity bookIdentity) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return List.of(
        List.of("Entity", bookIdentity.entityName().value()),
        List.of(
            "Accounting kernel", bookIdentity.bookDoctrine().accountingKernelProfileId().value()),
        List.of("Accounting basis", bookIdentity.bookDoctrine().accountingBasis().wireValue()),
        List.of("Functional currency", bookIdentity.functionalCurrency().code()),
        List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
  }
}
