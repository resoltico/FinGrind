package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Shared report-rendering helpers used by focused CLI report-family renderers. */
final class CliReportRenderSupport {
  static final int TEXT_TABLE_WIDTH = 120;

  private CliReportRenderSupport() {}

  static String joinedBalancesText(List<CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return CliQueryScopeText.zeroAcrossCurrenciesLabel();
    }
    return balances.stream()
        .map(CliBalanceOutputFormatter::displayBalanceText)
        .collect(Collectors.joining(", "));
  }

  static String renderStatementSection(
      String title, String table, List<CurrencyBalance> totals, String totalsLabel) {
    List<String> bodySections = new ArrayList<>();
    if (!table.isBlank()) {
      bodySections.add(table);
    }
    if (!totals.isEmpty()) {
      bodySections.add(
          CliTextFormat.renderKeyValueBlock(
              List.of(List.of(totalsLabel, joinedBalancesText(totals)))));
    }
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + joinSections(bodySections.toArray(String[]::new));
  }

  static String joinSections(String... sections) {
    return Arrays.stream(sections)
        .filter(section -> !section.isBlank())
        .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }

  static String section(String title, String body) {
    return title
        + System.lineSeparator()
        + "-".repeat(title.length())
        + System.lineSeparator()
        + body;
  }

  static String keyValueSection(String title, List<List<String>> rows) {
    return section(title, CliTextFormat.renderKeyValueBlock(rows));
  }

  static <SECTION, ROW> String renderStatementSections(
      List<SECTION> sections,
      String emptySectionLabel,
      Function<SECTION, String> sectionTitle,
      Function<SECTION, List<ROW>> sectionRows,
      Function<SECTION, List<CurrencyBalance>> sectionTotals,
      Function<ROW, List<String>> textRow) {
    return sections.stream()
        .map(
            section -> {
              List<ROW> rows = sectionRows.apply(section);
              return renderStatementSection(
                  sectionTitle.apply(section),
                  rows.isEmpty()
                      ? ""
                      : CliTextFormat.renderAdaptiveTable(
                          TEXT_TABLE_WIDTH,
                          List.of(
                              "Line code",
                              "Line name",
                              "Classification",
                              "Net amount",
                              "Balance side"),
                          rows.stream().map(textRow).toList(),
                          3),
                  sectionTotals.apply(section),
                  "Section totals");
            })
        .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
  }

  static <SECTION, ROW> String renderAccountTypeStatementSections(
      List<SECTION> sections,
      String emptySectionLabel,
      Function<SECTION, AccountType> sectionAccountType,
      Function<SECTION, List<ROW>> sectionRows,
      Function<SECTION, List<CurrencyBalance>> sectionTotals,
      Function<ROW, List<String>> textRow) {
    return renderStatementSections(
        sections,
        emptySectionLabel,
        section ->
            CliAccountStatementLabels.displayAccountTypeSectionLabel(
                sectionAccountType.apply(section)),
        sectionRows,
        sectionTotals,
        textRow);
  }

  static <SECTION> List<SECTION> renderableSections(
      List<SECTION> sections, Predicate<SECTION> hasRenderableSection) {
    return sections.stream().filter(hasRenderableSection).toList();
  }

  static <SECTION> List<String> emptyAccountTypeSectionLabels(
      List<SECTION> sections,
      Predicate<SECTION> hasRenderableSection,
      Function<SECTION, AccountType> sectionAccountType) {
    return sections.stream()
        .filter(section -> !hasRenderableSection.test(section))
        .map(
            section ->
                CliAccountStatementLabels.displayAccountTypeSectionLabel(
                    sectionAccountType.apply(section)))
        .toList();
  }

  static <SECTION> List<String> accountTypeSectionLabels(
      List<SECTION> sections, Function<SECTION, AccountType> sectionAccountType) {
    return sections.stream()
        .map(
            section ->
                CliAccountStatementLabels.displayAccountTypeSectionLabel(
                    sectionAccountType.apply(section)))
        .toList();
  }

  static List<List<String>> identityRows(
      BookIdentity bookIdentity, PostingCoverage postingCoverage, List<List<String>> rows) {
    List<List<String>> identityRows =
        new ArrayList<>(CliBookIdentityDisplay.contextRows(bookIdentity));
    identityRows.add(
        List.of("Posting coverage", CliPostingLabels.displayPostingCoverage(postingCoverage)));
    identityRows.addAll(rows);
    return List.copyOf(identityRows);
  }

  static String comparativeReferenceLine(EffectiveDateRange comparativeEffectiveDateRange) {
    if (comparativeEffectiveDateRange.effectiveDateFrom().isEmpty()
        && comparativeEffectiveDateRange.effectiveDateTo().isEmpty()) {
      return "(none)";
    }
    return CliQueryScopeText.dateRange(
        comparativeEffectiveDateRange.effectiveDateFrom().orElse(null),
        comparativeEffectiveDateRange.effectiveDateTo().orElse(null));
  }

  static String emptySectionLinesMessage(String sectionTitle) {
    String lowerTitle = sectionTitle.toLowerCase(Locale.ROOT);
    String baseName =
        lowerTitle.endsWith("ies")
            ? lowerTitle.substring(0, lowerTitle.length() - 3) + "y"
            : lowerTitle.endsWith("s")
                ? lowerTitle.substring(0, lowerTitle.length() - 1)
                : lowerTitle;
    return CliQueryScopeText.noMatchesLabel(baseName + " lines");
  }

  static CurrencyBalance balanceForCurrency(List<CurrencyBalance> balances, String currencyCode) {
    return balances.stream()
        .filter(balance -> balance.netAmount().currencyUnit().code().equals(currencyCode))
        .findFirst()
        .orElseGet(
            () ->
                CurrencyBalance.ofTotals(
                    Money.zero(CurrencyUnit.of(currencyCode)),
                    Money.zero(CurrencyUnit.of(currencyCode))));
  }
}
