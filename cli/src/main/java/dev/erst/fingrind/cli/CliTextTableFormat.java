package dev.erst.fingrind.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Renders CLI text tables and summary blocks with one fixed-width layout policy. */
final class CliTextTableFormat {
  private static final String TEXT_LINE_SEPARATOR = "\n";

  private CliTextTableFormat() {}

  /** Alignment modes for fixed-width CLI text cells. */
  private enum TextAlignment {
    LEFT,
    RIGHT
  }

  static String renderTable(
      List<String> headers, List<List<String>> rows, int... rightAlignedColumns) {
    Objects.requireNonNull(headers, "headers");
    Objects.requireNonNull(rows, "rows");
    int[] widths = tableWidths(headers, rows);
    Set<Integer> rightAligned = rightAligned(rightAlignedColumns);
    StringBuilder document = new StringBuilder();
    appendTableLine(document, headers, widths, rightAligned);
    appendSeparatorLine(document, widths);
    if (rows.isEmpty()) {
      document.append("(none)").append(TEXT_LINE_SEPARATOR);
      return document.toString().stripTrailing();
    }
    for (List<String> row : rows) {
      appendTableLine(document, row, widths, rightAligned);
    }
    return document.toString().stripTrailing();
  }

  static String renderAdaptiveTable(
      int maxWidth, List<String> headers, List<List<String>> rows, int... rightAlignedColumns) {
    Objects.requireNonNull(headers, "headers");
    Objects.requireNonNull(rows, "rows");
    int[] widths = tableWidths(headers, rows);
    int renderedWidth = tableWidth(widths);
    if (rows.isEmpty() || renderedWidth <= maxWidth) {
      return renderTable(headers, rows, rightAlignedColumns);
    }
    List<String> recordBlocks = new ArrayList<>();
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      recordBlocks.add(renderAdaptiveRecordBlock(maxWidth, headers, rows.get(rowIndex), rowIndex));
    }
    return String.join(TEXT_LINE_SEPARATOR + TEXT_LINE_SEPARATOR, recordBlocks);
  }

  private static void appendTableLine(
      StringBuilder document, List<String> row, int[] widths, Set<Integer> rightAligned) {
    StringBuilder line = new StringBuilder();
    for (int index = 0; index < row.size(); index++) {
      if (index > 0) {
        line.append(" | ");
      }
      line.append(
          padded(
              CliTextSafety.visible(row.get(index)),
              widths[index],
              rightAligned.contains(index) ? TextAlignment.RIGHT : TextAlignment.LEFT));
    }
    document.append(line.toString().stripTrailing()).append(TEXT_LINE_SEPARATOR);
  }

  private static void appendSeparatorLine(StringBuilder document, int[] widths) {
    List<String> cells = new ArrayList<>();
    for (int width : widths) {
      cells.add("-".repeat(width));
    }
    document.append(String.join("-+-", cells)).append(TEXT_LINE_SEPARATOR);
  }

  private static Set<Integer> rightAligned(int... rightAlignedColumns) {
    Set<Integer> rightAligned = new HashSet<>();
    for (int rightAlignedColumn : rightAlignedColumns) {
      rightAligned.add(rightAlignedColumn);
    }
    return Set.copyOf(rightAligned);
  }

  private static String padded(String value, int width, TextAlignment alignment) {
    int padding = width - CliTerminalWidth.cells(value);
    return alignment == TextAlignment.RIGHT
        ? " ".repeat(padding) + value
        : value + " ".repeat(padding);
  }

  private static int[] tableWidths(List<String> headers, List<List<String>> rows) {
    int columnCount = headers.size();
    int[] widths = new int[columnCount];
    for (int index = 0; index < columnCount; index++) {
      widths[index] = CliTerminalWidth.cells(headers.get(index));
    }
    for (List<String> row : rows) {
      if (row.size() != columnCount) {
        throw new IllegalArgumentException("Table row width does not match header width.");
      }
      for (int index = 0; index < columnCount; index++) {
        widths[index] = Math.max(widths[index], CliTerminalWidth.cells(row.get(index)));
      }
    }
    return widths;
  }

  private static int tableWidth(int[] widths) {
    int width = 0;
    for (int index = 0; index < widths.length; index++) {
      width += widths[index];
      if (index > 0) {
        width += 3;
      }
    }
    return width;
  }

  private static String renderAdaptiveRecordBlock(
      int maxWidth, List<String> headers, List<String> row, int rowIndex) {
    int summaryColumnCount = row.size() >= 2 ? 2 : 1;
    String summary =
        row.subList(0, summaryColumnCount).stream()
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining(" | "));
    if (summary.isBlank()) {
      summary = "Row " + (rowIndex + 1);
    }
    List<List<String>> detailRows = detailRows(headers, row, summaryColumnCount);
    return CliTextFormat.renderSummaryBlock(
        summary, CliTextKeyValueBlockFormat.renderKeyValueBlock(detailRows, maxWidth));
  }

  private static List<List<String>> detailRows(
      List<String> headers, List<String> row, int summaryColumnCount) {
    List<List<String>> detailRows =
        java.util.stream.IntStream.range(summaryColumnCount, headers.size())
            .mapToObj(columnIndex -> detailRow(headers, row, columnIndex))
            .toList();
    return detailRows.isEmpty()
        ? java.util.stream.IntStream.range(0, headers.size())
            .mapToObj(columnIndex -> detailRow(headers, row, columnIndex))
            .toList()
        : detailRows;
  }

  private static List<String> detailRow(List<String> headers, List<String> row, int columnIndex) {
    return List.of(headers.get(columnIndex), row.get(columnIndex));
  }
}
