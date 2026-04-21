package dev.erst.fingrind.cli;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared human-table, CSV, and money-display formatting for CLI query output. */
final class CliTextFormat {
  private static final String CSV_LINE_SEPARATOR = "\n";

  private CliTextFormat() {}

  /** Alignment modes for fixed-width CLI text cells. */
  private enum TextAlignment {
    LEFT,
    RIGHT
  }

  static String renderKeyValueBlock(List<List<String>> rows) {
    int labelWidth = rows.stream().mapToInt(row -> row.getFirst().length()).max().orElse(0);
    StringBuilder document = new StringBuilder();
    for (List<String> row : rows) {
      document
          .append(padded(row.getFirst(), labelWidth, TextAlignment.LEFT))
          .append(" : ")
          .append(row.get(1))
          .append(System.lineSeparator());
    }
    return document.toString().stripTrailing();
  }

  static String renderTitledBlock(String title, String body) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    return title
        + System.lineSeparator()
        + "=".repeat(title.length())
        + System.lineSeparator()
        + System.lineSeparator()
        + body;
  }

  static String renderTable(
      List<String> headers, List<List<String>> rows, int... rightAlignedColumns) {
    Objects.requireNonNull(headers, "headers");
    Objects.requireNonNull(rows, "rows");
    int columnCount = headers.size();
    int[] widths = new int[columnCount];
    for (int index = 0; index < columnCount; index++) {
      widths[index] = headers.get(index).length();
    }
    for (List<String> row : rows) {
      if (row.size() != columnCount) {
        throw new IllegalArgumentException("Table row width does not match header width.");
      }
      for (int index = 0; index < columnCount; index++) {
        widths[index] = Math.max(widths[index], row.get(index).length());
      }
    }
    Set<Integer> rightAligned = rightAligned(rightAlignedColumns);
    StringBuilder document = new StringBuilder();
    appendTableLine(document, headers, widths, rightAligned);
    appendSeparatorLine(document, widths);
    if (rows.isEmpty()) {
      document.append("(none)").append(System.lineSeparator());
      return document.toString().stripTrailing();
    }
    for (List<String> row : rows) {
      appendTableLine(document, row, widths, rightAligned);
    }
    return document.toString().stripTrailing();
  }

  static String renderCsv(List<String> headers, List<List<String>> rows) {
    Objects.requireNonNull(headers, "headers");
    Objects.requireNonNull(rows, "rows");
    StringBuilder document = new StringBuilder();
    document.append(csvRow(headers)).append(CSV_LINE_SEPARATOR);
    for (List<String> row : rows) {
      document.append(csvRow(row)).append(CSV_LINE_SEPARATOR);
    }
    return document.toString().stripTrailing();
  }

  static String displayAmount(String currencyCode, BigDecimal amount) {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Objects.requireNonNull(amount, "amount");
    int displayScale = Math.max(amount.scale(), currencyFractionDigits(currencyCode));
    return amount.setScale(displayScale, RoundingMode.UNNECESSARY).toPlainString();
  }

  static String joined(List<String> values) {
    return values.stream().filter(value -> !value.isBlank()).collect(Collectors.joining(", "));
  }

  private static void appendTableLine(
      StringBuilder document, List<String> row, int[] widths, Set<Integer> rightAligned) {
    for (int index = 0; index < row.size(); index++) {
      if (index > 0) {
        document.append(" | ");
      }
      document.append(
          padded(
              row.get(index),
              widths[index],
              rightAligned.contains(index) ? TextAlignment.RIGHT : TextAlignment.LEFT));
    }
    document.append(System.lineSeparator());
  }

  private static void appendSeparatorLine(StringBuilder document, int[] widths) {
    List<String> cells = new ArrayList<>();
    for (int width : widths) {
      cells.add("-".repeat(width));
    }
    document.append(String.join("-+-", cells)).append(System.lineSeparator());
  }

  private static Set<Integer> rightAligned(int... rightAlignedColumns) {
    Set<Integer> rightAligned = new HashSet<>();
    for (int rightAlignedColumn : rightAlignedColumns) {
      rightAligned.add(rightAlignedColumn);
    }
    return Set.copyOf(rightAligned);
  }

  private static String padded(String value, int width, TextAlignment alignment) {
    return alignment == TextAlignment.RIGHT
        ? " ".repeat(width - value.length()) + value
        : value + " ".repeat(width - value.length());
  }

  private static String csvRow(List<String> values) {
    return values.stream().map(CliTextFormat::csvCell).collect(Collectors.joining(","));
  }

  private static String csvCell(String value) {
    String escaped = value.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }

  private static int currencyFractionDigits(String currencyCode) {
    try {
      int digits =
          Currency.getInstance(currencyCode.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
      return digits < 0 ? 2 : digits;
    } catch (IllegalArgumentException exception) {
      return 2;
    }
  }
}
