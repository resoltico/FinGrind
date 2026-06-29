package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.core.BookDoctrineDisplay;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared statement metadata rows for PDF statement renderers. */
final class PdfStatementMetadataRows {
  private PdfStatementMetadataRows() {}

  static List<List<String>> reportParameters(
      BookIdentity bookIdentity, PostingCoverage postingCoverage, List<List<String>> rows) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    Objects.requireNonNull(rows, "rows");
    List<List<String>> statementRows = new ArrayList<>();
    statementRows.add(List.of("Entity", bookIdentity.entityName().value()));
    statementRows.add(
        List.of(
            "Starter chart",
            BookDoctrineDisplay.bookTemplate(bookIdentity.bookDoctrine().bookTemplateId())));
    statementRows.add(
        List.of(
            "Accounting basis",
            BookDoctrineDisplay.accountingBasis(bookIdentity.bookDoctrine().accountingBasis())));
    statementRows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    statementRows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    statementRows.add(
        List.of(
            "Posting coverage", PdfPostingValueFormatter.displayPostingCoverage(postingCoverage)));
    statementRows.addAll(rows);
    return List.copyOf(statementRows);
  }

  static List<List<String>> statementParameters(
      BookIdentity bookIdentity,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<List<String>> rows) {
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    return reportParameters(bookIdentity, postingCoverage, rows);
  }
}
