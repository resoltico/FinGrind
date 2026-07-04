package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.ReportColumn;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportRow;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.contract.reportmodel.ReportTotals;
import dev.erst.fingrind.contract.reportmodel.ReportVerdict;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses the text projector output into the shared cross-format fact set. */
final class ReportCrossFormatTextFacts {
  private ReportCrossFormatTextFacts() {}

  static Set<String> fromText(ReportModel model, String rendered) {
    TextDocumentParser parser = new TextDocumentParser(rendered);
    Set<String> facts = new LinkedHashSet<>();
    parser.consumeExact(model.title());
    parser.consumeExact("=".repeat(model.title().length()));
    parser.consumeBlankLines();
    if (!model.verdicts().isEmpty()) {
      appendVerdictFacts(parser, facts, "summary", model.verdicts());
      parser.consumeBlankLines();
    }
    for (ReportSection section : model.sections()) {
      parser.consumeHeading(section.title());
      facts.add(ReportCrossFormatStructuredFacts.sectionTitleFact(section.key(), section.title()));
      if (!section.verdicts().isEmpty()) {
        appendVerdictFacts(parser, facts, section.key(), section.verdicts());
        parser.consumeBlankLines();
      }
      if (!section.rows().isEmpty()) {
        appendTableFacts(parser, facts, section.key(), "table", section.columns(), section.rows());
        parser.consumeBlankLines();
      }
      if (section.verdicts().isEmpty() && section.rows().isEmpty() && section.totals().isEmpty()) {
        appendVerdictFacts(
            parser,
            facts,
            section.key(),
            List.of(new ReportVerdict("Outcome", "No projected facts.")));
        parser.consumeBlankLines();
      }
      for (ReportTotals totals : section.totals()) {
        parser.consumeHeading(totals.title());
        facts.add(ReportCrossFormatStructuredFacts.totalsTitleFact(section.key(), totals.title()));
        appendTableFacts(
            parser, facts, section.key(), totals.title(), totals.columns(), totals.rows());
        parser.consumeBlankLines();
      }
    }
    parser.consumeHeading("Context");
    appendVerdictFacts(parser, facts, "context", model.context().rows());
    parser.consumeBlankLines();
    parser.assertExhausted();
    return facts;
  }

  private static void appendVerdictFacts(
      TextDocumentParser parser,
      Set<String> facts,
      String sectionKey,
      List<ReportVerdict> verdicts) {
    for (ReportVerdict verdict : verdicts) {
      String actualValue = parser.consumeKeyValue(verdict.label());
      assertEquals(
          normalized(verdict.value()),
          normalized(actualValue),
          () -> "Mismatched text verdict value for " + sectionKey + " / " + verdict.label());
      facts.add(
          ReportCrossFormatStructuredFacts.verdictFact(
              sectionKey, verdict.label(), verdict.value()));
    }
  }

  private static void appendTableFacts(
      TextDocumentParser parser,
      Set<String> facts,
      String sectionKey,
      String blockTitle,
      List<ReportColumn> columns,
      List<ReportRow> rows) {
    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
      ReportColumn column = columns.get(columnIndex);
      facts.add(
          ReportCrossFormatStructuredFacts.columnFact(
              sectionKey, blockTitle, columnIndex, column.key(), column.title()));
    }
    if (parser.looksLikeStandardTable()) {
      List<String> headerCells = parser.consumeStandardTableHeader();
      assertEquals(
          columns.stream().map(ReportColumn::title).toList(),
          headerCells,
          () -> "Mismatched text table headers for " + sectionKey + " / " + blockTitle);
      if (rows.isEmpty()) {
        parser.consumeExact("(none)");
        return;
      }
      for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
        List<String> actualCells = parser.consumeStandardTableRow();
        List<String> expectedCells = rows.get(rowIndex).cells();
        int expectedRowIndex = rowIndex;
        assertEquals(
            expectedCells,
            actualCells,
            () ->
                "Mismatched text table row for "
                    + sectionKey
                    + " / "
                    + blockTitle
                    + " / row "
                    + expectedRowIndex);
        appendRowFacts(facts, sectionKey, blockTitle, expectedRowIndex, columns, expectedCells);
      }
      return;
    }
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      List<String> expectedCells = rows.get(rowIndex).cells();
      int summaryColumnCount = expectedCells.size() >= 2 ? 2 : 1;
      String expectedSummary =
          expectedCells.subList(0, summaryColumnCount).stream()
              .filter(value -> !value.isBlank())
              .reduce((left, right) -> left + " | " + right)
              .orElse("Row " + (rowIndex + 1));
      parser.consumeExact(expectedSummary);
      parser.consumeExact("-".repeat(expectedSummary.length()));
      int detailStart = summaryColumnCount < expectedCells.size() ? summaryColumnCount : 0;
      for (int columnIndex = detailStart; columnIndex < columns.size(); columnIndex++) {
        String actualValue = parser.consumeKeyValue(columns.get(columnIndex).title());
        int expectedRowIndex = rowIndex;
        int expectedColumnIndex = columnIndex;
        assertEquals(
            normalized(expectedCells.get(columnIndex)),
            normalized(actualValue),
            () ->
                "Mismatched adaptive text value for "
                    + sectionKey
                    + " / "
                    + blockTitle
                    + " / row "
                    + expectedRowIndex
                    + " / "
                    + columns.get(expectedColumnIndex).title());
      }
      parser.consumeBlankLines();
      appendRowFacts(facts, sectionKey, blockTitle, rowIndex, columns, expectedCells);
    }
  }

  private static void appendRowFacts(
      Set<String> facts,
      String sectionKey,
      String blockTitle,
      int rowIndex,
      List<ReportColumn> columns,
      List<String> cells) {
    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
      facts.add(
          ReportCrossFormatStructuredFacts.cellFact(
              sectionKey,
              blockTitle,
              rowIndex,
              columnIndex,
              columns.get(columnIndex).key(),
              cells.get(columnIndex)));
    }
  }

  private static String normalized(String value) {
    return value.replaceAll("\\s+", " ").trim();
  }

  /** Stateful parser for titled CLI text blocks, key-value rows, and adaptive table variants. */
  private static final class TextDocumentParser {
    private final List<String> lines;
    private int index;

    private TextDocumentParser(String rendered) {
      this.lines = rendered.lines().toList();
    }

    private void consumeExact(String expected) {
      assertTrue(index < lines.size(), () -> "Missing expected text line: " + expected);
      assertEquals(expected, lines.get(index), () -> "Unexpected text line at " + index);
      index++;
    }

    private void consumeHeading(String title) {
      consumeBlankLines();
      consumeExact(title);
      consumeExact("-".repeat(title.length()));
    }

    private void consumeBlankLines() {
      while (index < lines.size() && lines.get(index).isBlank()) {
        index++;
      }
    }

    private String consumeKeyValue(String expectedLabel) {
      consumeBlankLines();
      assertTrue(index < lines.size(), () -> "Missing key-value row for " + expectedLabel);
      String line = lines.get(index);
      int separatorIndex = line.indexOf(" : ");
      assertTrue(
          separatorIndex >= 0,
          () -> "Missing key-value separator for " + expectedLabel + ": " + line);
      String actualLabel = line.substring(0, separatorIndex).stripTrailing();
      assertEquals(expectedLabel, actualLabel, () -> "Unexpected key-value label at " + index);
      List<String> valueParts = new ArrayList<>();
      valueParts.add(line.substring(separatorIndex + 3).trim());
      index++;
      while (index < lines.size() && lines.get(index).startsWith(" ")) {
        valueParts.add(lines.get(index).trim());
        index++;
      }
      return String.join(" ", valueParts).trim();
    }

    private boolean looksLikeStandardTable() {
      return index + 1 < lines.size()
          && lines.get(index).contains(" | ")
          && lines.get(index + 1).contains("-+-");
    }

    private List<String> consumeStandardTableHeader() {
      consumeBlankLines();
      List<String> headerCells = splitTableCells(lines.get(index));
      index++;
      String separator = lines.get(index);
      assertTrue(separator.contains("-+-"), () -> "Missing standard-table separator at " + index);
      index++;
      return headerCells;
    }

    private List<String> consumeStandardTableRow() {
      assertTrue(index < lines.size(), "Missing standard-table row");
      List<String> cells = splitTableCells(lines.get(index));
      index++;
      return cells;
    }

    private void assertExhausted() {
      consumeBlankLines();
      assertEquals(lines.size(), index, () -> "Unexpected trailing text starting at line " + index);
    }

    private static List<String> splitTableCells(String line) {
      return java.util.Arrays.stream(line.split(" \\| ", -1)).map(String::strip).toList();
    }
  }
}
