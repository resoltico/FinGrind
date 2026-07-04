package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.StatementLineKind;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** Statement-specific section builders shared by financial-position and income-statement models. */
final class ReportStatementModelSupport {
  private ReportStatementModelSupport() {}

  static boolean hasRenderableContent(List<?> rows, List<?> totals) {
    return !rows.isEmpty() || !totals.isEmpty();
  }

  static ReportSection accountTypeStatementSection(
      String sectionPrefix,
      String titlePrefix,
      AccountType accountType,
      List<ReportRow> rows,
      List<CurrencyBalance> totals) {
    return statementSection(
        sectionPrefix + "-" + accountType.wireValue(),
        titlePrefix + ReportModelClassificationDisplay.displayAccountTypeSection(accountType),
        rows,
        totals);
  }

  static ReportSection statementSection(
      String key, String title, List<ReportRow> rows, List<CurrencyBalance> totals) {
    return ReportModelSupport.section(
        key,
        title,
        List.of(),
        statementSectionColumns(),
        rows,
        totals.isEmpty()
            ? List.of()
            : List.of(
                ReportModelSupport.totals(
                    key + "-totals",
                    title + " Totals",
                    ReportModelSupport.balanceColumns(),
                    ReportModelSupport.balanceRows(totals))));
  }

  static List<ReportColumn> statementSectionColumns() {
    return List.of(
        ReportModelSupport.leftColumn("lineCode", "Line code"),
        ReportModelSupport.leftColumn("lineName", "Line name"),
        ReportModelSupport.leftColumn("lineKind", "Row kind"),
        ReportModelSupport.leftColumn("classification", "Classification"),
        ReportModelSupport.rightColumn("netAmount", "Net amount"),
        ReportModelSupport.leftColumn("balanceSide", "Balance side"));
  }

  static ReportRow statementSectionRow(
      String lineCode,
      String currencyCode,
      String lineName,
      StatementLineKind lineKind,
      String classification,
      Money netAmount,
      BalanceSide balanceSide) {
    return ReportModelSupport.row(
        lineCode + ":" + currencyCode,
        ReportModelDisplay.displayStatementLineCode(lineCode, lineKind),
        lineName,
        ReportModelDisplay.displayStatementLineKind(lineKind),
        classification,
        ReportModelDisplay.displayMoney(netAmount),
        ReportModelDisplay.displayBalanceSide(balanceSide));
  }

  static <SECTION> List<String> emptyAccountTypeSectionLabels(
      List<SECTION> sections,
      Predicate<SECTION> hasContent,
      Function<SECTION, AccountType> accountTypeExtractor) {
    return sections.stream()
        .filter(section -> !hasContent.test(section))
        .map(accountTypeExtractor)
        .map(ReportModelClassificationDisplay::displayAccountTypeSection)
        .toList();
  }

  static <SECTION> List<String> renderableAccountTypeSectionLabels(
      List<SECTION> sections,
      Predicate<SECTION> hasContent,
      Function<SECTION, AccountType> accountTypeExtractor) {
    return sections.stream()
        .filter(hasContent)
        .map(accountTypeExtractor)
        .map(ReportModelClassificationDisplay::displayAccountTypeSection)
        .toList();
  }
}
