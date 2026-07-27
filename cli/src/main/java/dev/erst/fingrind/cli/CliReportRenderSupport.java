package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.EffectiveDateRange;
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

  static String renderPagedListText(CliPagedListText page) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            page.returnedCount() == 0
                ? List.of(
                    List.of("Outcome", CliQueryScopeText.noMatchesLabel(page.emptySubjectPlural())),
                    List.of("Limit", Integer.toString(page.limit())),
                    List.of("Next cursor", page.nextCursor()))
                : List.of(
                    List.of(
                        "Returned " + page.returnedSubjectPlural(),
                        Integer.toString(page.returnedCount())),
                    List.of("Limit", Integer.toString(page.limit())),
                    List.of("Next cursor", page.nextCursor())));
    return CliTextFormat.renderTitledBlock(
        page.title(),
        joinSections(
            summary,
            page.renderedRows(),
            page.withContext() ? keyValueSection("Context", page.contextRows()) : ""));
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
}
