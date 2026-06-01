package dev.erst.fingrind.report.pdf;

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
            "Book context",
            "Currency "
                + bookIdentity.functionalCurrency().code()
                + " / Fiscal year start "
                + bookIdentity.fiscalYearStart().wireValue()
                + " / Posting coverage "
                + PdfValueFormatter.displayPostingCoverage(postingCoverage)));
    String businessActivity =
        PdfValueFormatter.displayBusinessActivityTags(
            bookIdentity.entityProfile().businessActivityTags());
    if (!"(none)".equals(businessActivity)) {
      statementRows.add(List.of("Business activity", businessActivity));
    }
    statementRows.addAll(rows);
    return List.copyOf(statementRows);
  }

  static List<List<String>> statementParameters(
      BookIdentity bookIdentity,
      EffectiveDateRange comparativeEffectiveDateRange,
      PostingCoverage postingCoverage,
      List<List<String>> rows) {
    Objects.requireNonNull(comparativeEffectiveDateRange, "comparativeEffectiveDateRange");
    List<List<String>> statementRows =
        new ArrayList<>(reportParameters(bookIdentity, postingCoverage, List.of()));
    String comparativeRange =
        PdfTemporalValueFormatter.comparativeRange(comparativeEffectiveDateRange);
    if (!"(none)".equals(comparativeRange)) {
      statementRows.add(List.of("Comparative range", comparativeRange));
    }
    statementRows.addAll(rows);
    return List.copyOf(statementRows);
  }
}
