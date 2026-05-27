package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CurrencyBalance;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/** Shared statement-section table rendering for balance-style financial statements. */
final class PdfStatementSectionTableRenderer {
  private PdfStatementSectionTableRenderer() {}

  static <S, R> void renderSections(
      PdfPageWriter pageWriter,
      List<S> sections,
      String titlePrefix,
      Function<S, AccountType> accountTypeAccessor,
      Function<S, List<R>> rowAccessor,
      Function<S, List<CurrencyBalance>> totalsAccessor,
      Function<R, List<String>> rowRenderer)
      throws IOException {
    for (S section : sections) {
      List<R> rows = rowAccessor.apply(section);
      List<CurrencyBalance> totals = totalsAccessor.apply(section);
      if (rows.isEmpty() && totals.isEmpty()) {
        continue;
      }
      String sectionTitle =
          titlePrefix
              + PdfValueFormatter.displayAccountTypeSection(accountTypeAccessor.apply(section));
      pageWriter.writeTable(
          sectionTitle,
          PdfReportTableLayouts.statementBalanceColumns(),
          rows.stream().map(rowRenderer).toList());
      if (!totals.isEmpty()) {
        PdfBalanceTableSupport.writeSummaryTable(pageWriter, sectionTitle + " Totals", totals);
      }
    }
  }
}
