package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.reportmodel.ReportColumn;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.ReportRow;
import dev.erst.fingrind.contract.reportmodel.ReportSection;
import dev.erst.fingrind.contract.reportmodel.ReportTotals;
import dev.erst.fingrind.contract.reportmodel.ReportVerdict;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses PDFBox text extraction output into the shared cross-format fact set. */
final class ReportCrossFormatPdfFacts {
  private ReportCrossFormatPdfFacts() {}

  static Set<String> fromPdfText(ReportModel model, String pdfText) {
    PdfTextParser parser = new PdfTextParser(model.title(), pdfText);
    Set<String> facts = new LinkedHashSet<>();
    parser.consumeLiteralExact(model.title());
    parser.consumeLiteralGeneratedLine();
    if (!model.verdicts().isEmpty()) {
      parser.consumeExact("Summary");
      appendVerdictFacts(parser, facts, "summary", model.verdicts());
    }
    for (ReportSection section : model.sections()) {
      parser.consumeExact(section.title());
      facts.add(ReportCrossFormatStructuredFacts.sectionTitleFact(section.key(), section.title()));
      if (!section.verdicts().isEmpty()) {
        appendVerdictFacts(parser, facts, section.key(), section.verdicts());
      }
      if (!section.rows().isEmpty()) {
        appendTableFacts(parser, facts, section.key(), "table", section.columns(), section.rows());
      }
      for (ReportTotals totals : section.totals()) {
        parser.consumeExact(totals.title());
        facts.add(ReportCrossFormatStructuredFacts.totalsTitleFact(section.key(), totals.title()));
        appendTableFacts(
            parser, facts, section.key(), totals.title(), totals.columns(), totals.rows());
      }
    }
    parser.consumeExact("Context");
    appendVerdictFacts(parser, facts, "context", model.context().rows());
    parser.assertExhausted();
    return facts;
  }

  private static void appendVerdictFacts(
      PdfTextParser parser, Set<String> facts, String sectionKey, List<ReportVerdict> verdicts) {
    for (ReportVerdict verdict : verdicts) {
      parser.consumeNormalizedLine(verdict.label() + " " + verdict.value());
      facts.add(
          ReportCrossFormatStructuredFacts.verdictFact(
              sectionKey, verdict.label(), verdict.value()));
    }
  }

  private static void appendTableFacts(
      PdfTextParser parser,
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
    String headerLine =
        columns.stream().map(ReportColumn::title).reduce((l, r) -> l + " " + r).orElse("");
    parser.consumeNormalizedLine(headerLine);
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      List<String> expectedCells = rows.get(rowIndex).cells();
      parser.consumeRepeatedHeaderIfPresent(headerLine);
      parser.consumeNormalizedLine(expectedCells.stream().reduce((l, r) -> l + " " + r).orElse(""));
      for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
        facts.add(
            ReportCrossFormatStructuredFacts.cellFact(
                sectionKey,
                blockTitle,
                rowIndex,
                columnIndex,
                columns.get(columnIndex).key(),
                expectedCells.get(columnIndex)));
      }
    }
  }

  /** Stateful parser for the flattened PDFBox text layer, including repeated page mastheads. */
  private static final class PdfTextParser {
    private final String reportTitle;
    private final List<String> lines;
    private int index;

    private PdfTextParser(String reportTitle, String rendered) {
      this.reportTitle = reportTitle;
      this.lines = rendered.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    private void consumeLiteralExact(String expected) {
      assertTrue(index < lines.size(), () -> "Missing expected PDF line: " + expected);
      assertEquals(expected, lines.get(index), () -> "Unexpected PDF line at " + index);
      index++;
    }

    private void consumeExact(String expected) {
      skipNoise();
      consumeLiteralExact(expected);
    }

    private void consumeLiteralGeneratedLine() {
      assertTrue(index < lines.size(), "Missing PDF masthead line");
      assertTrue(
          lines.get(index).startsWith("Generated ") && lines.get(index).contains(" / Prepared by "),
          () -> "Unexpected PDF masthead line: " + lines.get(index));
      index++;
    }

    private void consumeNormalizedLine(String expected) {
      skipNoise();
      String expectedNormalized = normalized(expected);
      String expectedCompact = compact(expected);
      StringBuilder actual = new StringBuilder();
      int startIndex = index;
      while (index < lines.size()) {
        skipNoise();
        if (index >= lines.size()) {
          break;
        }
        if (!actual.isEmpty()) {
          actual.append(' ');
        }
        actual.append(lines.get(index));
        index++;
        if (compact(actual.toString()).equals(expectedCompact)) {
          return;
        }
        if (compact(actual.toString()).length() > expectedCompact.length()) {
          break;
        }
      }
      throw new AssertionError(
          "Missing expected PDF text starting at line "
              + startIndex
              + ": "
              + expectedNormalized
              + " but saw "
              + normalized(actual.toString()));
    }

    private void consumeRepeatedHeaderIfPresent(String expected) {
      skipNoise();
      if (index < lines.size() && normalized(lines.get(index)).equals(normalized(expected))) {
        index++;
      }
    }

    private void assertExhausted() {
      skipNoise();
      assertEquals(lines.size(), index, () -> "Unexpected trailing PDF text at line " + index);
    }

    private void skipNoise() {
      while (index < lines.size()) {
        String line = lines.get(index);
        boolean isNoise =
            line.equals(reportTitle)
                || (line.startsWith("Generated ") && line.contains(" / Prepared by "))
                || line.matches("\\d+ / \\d+");
        if (!isNoise) {
          break;
        }
        index++;
      }
    }

    private static String normalized(String value) {
      return value.replaceAll("\\s+", " ").trim();
    }

    private static String compact(String value) {
      return value.replaceAll("\\s+", "");
    }
  }
}
