package dev.erst.fingrind.cli;

import java.util.List;

/**
 * Renders CLI key-value blocks, including the capped discovery-help layout branch where over-cap
 * labels become header lines for aligned continuation descriptions.
 */
final class CliTextKeyValueBlockFormat {
  private static final String TEXT_LINE_SEPARATOR = "\n";
  private static final int MINIMUM_WRAP_VALUE_WIDTH = 24;

  private CliTextKeyValueBlockFormat() {}

  static String renderKeyValueBlock(List<List<String>> rows) {
    return renderKeyValueBlock(rows, Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  static String renderKeyValueBlock(List<List<String>> rows, int totalWidth) {
    return renderKeyValueBlock(rows, totalWidth, Integer.MAX_VALUE);
  }

  static String renderKeyValueBlock(List<List<String>> rows, int totalWidth, int labelWidthCap) {
    if (labelWidthCap < Integer.MAX_VALUE) {
      return renderCappedKeyValueBlock(rows, totalWidth, Math.max(1, labelWidthCap));
    }
    return renderUncappedKeyValueBlock(rows, totalWidth);
  }

  private static String renderUncappedKeyValueBlock(List<List<String>> rows, int totalWidth) {
    int labelWidth =
        rows.stream().mapToInt(row -> CliTerminalWidth.cells(row.getFirst())).max().orElse(0);
    StringBuilder document = new StringBuilder();
    for (List<String> row : rows) {
      String label = CliTextSafety.visible(row.getFirst());
      int rowLabelWidth = Math.max(labelWidth, CliTerminalWidth.cells(label));
      List<String> wrappedValueLines =
          CliTextWrap.wrapLines(
              CliTextSafety.visible(row.get(1)),
              totalWidth == Integer.MAX_VALUE
                  ? Integer.MAX_VALUE
                  : Math.max(MINIMUM_WRAP_VALUE_WIDTH, totalWidth - rowLabelWidth - 3));
      document
          .append(padded(label, rowLabelWidth))
          .append(" : ")
          .append(wrappedValueLines.getFirst())
          .append(TEXT_LINE_SEPARATOR);
      appendWrappedValueLines(document, wrappedValueLines, rowLabelWidth, 1);
    }
    return document.toString().stripTrailing();
  }

  private static String renderCappedKeyValueBlock(
      List<List<String>> rows, int totalWidth, int labelWidthCap) {
    int valueWidth =
        totalWidth == Integer.MAX_VALUE
            ? Integer.MAX_VALUE
            : Math.max(MINIMUM_WRAP_VALUE_WIDTH, totalWidth - labelWidthCap - 3);
    StringBuilder document = new StringBuilder();
    for (List<String> row : rows) {
      String label = CliTextSafety.visible(row.getFirst());
      List<String> wrappedValueLines =
          CliTextWrap.wrapLines(CliTextSafety.visible(row.get(1)), valueWidth);
      if (CliTerminalWidth.cells(label) <= labelWidthCap) {
        appendInlineRow(document, label, wrappedValueLines, labelWidthCap);
        continue;
      }
      appendOverCapHeaderRow(document, label, wrappedValueLines, labelWidthCap);
    }
    return document.toString().stripTrailing();
  }

  private static void appendInlineRow(
      StringBuilder document, String label, List<String> wrappedValueLines, int labelWidth) {
    document
        .append(padded(label, labelWidth))
        .append(" : ")
        .append(wrappedValueLines.getFirst())
        .append(TEXT_LINE_SEPARATOR);
    appendWrappedValueLines(document, wrappedValueLines, labelWidth, 1);
  }

  private static void appendOverCapHeaderRow(
      StringBuilder document, String label, List<String> wrappedValueLines, int labelWidth) {
    // In capped discovery help, an over-cap key becomes a block header. The aligned
    // continuation line owns the description column.
    document.append(label).append(':').append(TEXT_LINE_SEPARATOR);
    appendWrappedValueLines(document, wrappedValueLines, labelWidth, 0);
  }

  private static void appendWrappedValueLines(
      StringBuilder document, List<String> wrappedValueLines, int labelWidth, int fromIndex) {
    for (int index = fromIndex; index < wrappedValueLines.size(); index++) {
      document
          .append(" ".repeat(labelWidth))
          .append("   ")
          .append(wrappedValueLines.get(index))
          .append(TEXT_LINE_SEPARATOR);
    }
  }

  private static String padded(String value, int width) {
    return value + " ".repeat(width - CliTerminalWidth.cells(value));
  }
}
