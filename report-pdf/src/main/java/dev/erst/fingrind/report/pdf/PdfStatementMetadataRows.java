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
            "Business activity",
            PdfValueFormatter.displayBusinessActivityTags(
                bookIdentity.entityProfile().businessActivityTags())));
    statementRows.add(List.of("Functional currency", bookIdentity.functionalCurrency().code()));
    statementRows.add(List.of("Fiscal year start", bookIdentity.fiscalYearStart().wireValue()));
    statementRows.add(
        List.of(
            "Policy profile",
            PdfValueFormatter.displayPolicyProfile(bookIdentity.policyProfile())));
    statementRows.add(
        List.of("Posting coverage", PdfValueFormatter.displayPostingCoverage(postingCoverage)));
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
    statementRows.add(
        List.of(
            "Comparative range",
            PdfValueFormatter.comparativeRange(comparativeEffectiveDateRange)));
    statementRows.addAll(rows);
    return List.copyOf(statementRows);
  }
}
