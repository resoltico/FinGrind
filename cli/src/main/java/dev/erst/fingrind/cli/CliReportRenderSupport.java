package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.Money;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Shared text-rendering helpers used across CLI output surfaces. */
final class CliReportRenderSupport {
  static final int TEXT_TABLE_WIDTH = 120;

  private CliReportRenderSupport() {}

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
            () -> {
              CurrencyUnit currencyUnit = CurrencyUnit.of(currencyCode);
              return CurrencyBalance.ofTotals(Money.zero(currencyUnit), Money.zero(currencyUnit));
            });
  }
}
