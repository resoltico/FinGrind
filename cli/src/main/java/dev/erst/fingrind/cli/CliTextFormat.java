package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.Money;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Shared text-table, CSV, and money-display formatting for CLI query output. */
final class CliTextFormat {
  private static final String TEXT_LINE_SEPARATOR = "\n";
  private static final String CSV_LINE_SEPARATOR = "\n";
  private static final int MINIMUM_WRAP_VALUE_WIDTH = 24;
  private static final Pattern WRAP_WORD_BOUNDARY = Pattern.compile("\\s+");
  private static final String WRAP_PREFERRED_BREAKS = "/._-";
  private static final Pattern SHELL_TOKEN =
      Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|\\S+");

  private CliTextFormat() {}

  /** Alignment modes for fixed-width CLI text cells. */
  private enum TextAlignment {
    LEFT,
    RIGHT
  }

  static String renderKeyValueBlock(List<List<String>> rows) {
    return renderKeyValueBlock(rows, Integer.MAX_VALUE);
  }

  static String renderKeyValueBlock(List<List<String>> rows, int totalWidth) {
    int labelWidth = rows.stream().mapToInt(row -> row.getFirst().length()).max().orElse(0);
    StringBuilder document = new StringBuilder();
    for (List<String> row : rows) {
      List<String> wrappedValueLines =
          wrapLines(
              row.get(1),
              totalWidth == Integer.MAX_VALUE
                  ? Integer.MAX_VALUE
                  : Math.max(MINIMUM_WRAP_VALUE_WIDTH, totalWidth - labelWidth - 3));
      document
          .append(padded(row.getFirst(), labelWidth, TextAlignment.LEFT))
          .append(" : ")
          .append(wrappedValueLines.getFirst())
          .append(TEXT_LINE_SEPARATOR);
      for (int index = 1; index < wrappedValueLines.size(); index++) {
        document
            .append(" ".repeat(labelWidth))
            .append("   ")
            .append(wrappedValueLines.get(index))
            .append(TEXT_LINE_SEPARATOR);
      }
    }
    return document.toString().stripTrailing();
  }

  static String renderTitledBlock(String title, String body) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    return title
        + TEXT_LINE_SEPARATOR
        + "=".repeat(title.length())
        + TEXT_LINE_SEPARATOR
        + TEXT_LINE_SEPARATOR
        + body;
  }

  static String renderSummaryBlock(String summary, String body) {
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(body, "body");
    return summary
        + TEXT_LINE_SEPARATOR
        + "-".repeat(summary.length())
        + TEXT_LINE_SEPARATOR
        + body;
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

  static String displayMoney(Money money) {
    Objects.requireNonNull(money, "money");
    return money.canonicalDecimal();
  }

  static String joined(List<String> values) {
    return values.stream().filter(value -> !value.isBlank()).collect(Collectors.joining(", "));
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
              row.get(index),
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

  static String wrap(String text, int width) {
    return String.join(TEXT_LINE_SEPARATOR, wrapLines(text, width));
  }

  static String wrapLineBlock(List<String> lines, int width) {
    Objects.requireNonNull(lines, "lines");
    return lines.isEmpty()
        ? ""
        : lines.stream()
            .map(line -> wrap(line, width))
            .collect(Collectors.joining(TEXT_LINE_SEPARATOR));
  }

  static String renderBulletedBlock(List<String> items, int width) {
    Objects.requireNonNull(items, "items");
    return items.isEmpty()
        ? ""
        : items.stream()
            .map(item -> wrapWithPrefix(item, width, "- ", "  "))
            .collect(Collectors.joining(TEXT_LINE_SEPARATOR));
  }

  static String renderLiteralBlock(List<String> lines, String prefix) {
    Objects.requireNonNull(lines, "lines");
    Objects.requireNonNull(prefix, "prefix");
    return lines.isEmpty()
        ? ""
        : lines.stream()
            .map(line -> prefix + Objects.requireNonNull(line, "line"))
            .collect(Collectors.joining(TEXT_LINE_SEPARATOR));
  }

  static String renderShellCommandBlock(List<String> commands, int width) {
    Objects.requireNonNull(commands, "commands");
    if (commands.isEmpty()) {
      return "";
    }
    return commands.stream()
        .map(command -> renderShellCommand(command, width))
        .collect(Collectors.joining(TEXT_LINE_SEPARATOR + TEXT_LINE_SEPARATOR));
  }

  private static List<String> wrapLines(String text, int width) {
    Objects.requireNonNull(text, "text");
    if (width == Integer.MAX_VALUE || text.isBlank()) {
      return text.lines().toList().isEmpty() ? List.of(text) : text.lines().toList();
    }
    List<String> lines = new ArrayList<>();
    StringBuilder currentLine = new StringBuilder();
    for (String sourceLine : text.lines().toList()) {
      if (sourceLine.length() <= width) {
        lines.add(sourceLine);
        continue;
      }
      currentLine.setLength(0);
      Matcher wordMatcher = WRAP_WORD_BOUNDARY.matcher(sourceLine);
      int cursor = 0;
      while (cursor < sourceLine.length()) {
        while (cursor < sourceLine.length() && Character.isWhitespace(sourceLine.charAt(cursor))) {
          cursor++;
        }
        if (cursor >= sourceLine.length()) {
          break;
        }
        wordMatcher.region(cursor, sourceLine.length());
        int nextBoundary = wordMatcher.find() ? wordMatcher.start() : sourceLine.length();
        String word = sourceLine.substring(cursor, nextBoundary);
        for (String wordPart : splitLongToken(word, width)) {
          if (currentLine.isEmpty()) {
            currentLine.append(wordPart);
          } else if (currentLine.length() + 1 + wordPart.length() <= width) {
            currentLine.append(' ').append(wordPart);
          } else {
            lines.add(currentLine.toString());
            currentLine.setLength(0);
            currentLine.append(wordPart);
          }
        }
        cursor = nextBoundary;
      }
      if (!currentLine.isEmpty()) {
        lines.add(currentLine.toString());
      }
    }
    return List.copyOf(lines);
  }

  private static String wrapWithPrefix(
      String text, int width, String firstPrefix, String nextPrefix) {
    int firstWidth =
        width == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, width - firstPrefix.length());
    int nextWidth =
        width == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, width - nextPrefix.length());
    List<String> wrappedLines = wrapLines(text, firstWidth);
    StringBuilder builder = new StringBuilder(firstPrefix).append(wrappedLines.getFirst());
    for (int index = 1; index < wrappedLines.size(); index++) {
      builder
          .append(TEXT_LINE_SEPARATOR)
          .append(nextPrefix)
          .append(
              String.join(
                  TEXT_LINE_SEPARATOR + nextPrefix, wrapLines(wrappedLines.get(index), nextWidth)));
    }
    return builder.toString();
  }

  private static List<String> splitLongToken(String token, int width) {
    if (token.length() <= width) {
      return List.of(token);
    }
    List<String> segments = new ArrayList<>();
    int segmentStart = 0;
    while (true) {
      int remaining = token.length() - segmentStart;
      if (remaining <= width) {
        segments.add(token.substring(segmentStart));
        return List.copyOf(segments);
      }
      int split = preferredSplitIndex(token, segmentStart, width);
      segments.add(token.substring(segmentStart, split));
      segmentStart = split;
    }
  }

  private static String renderShellCommand(String command, int width) {
    Objects.requireNonNull(command, "command");
    List<String> tokens = shellTokens(command);
    if (tokens.isEmpty()) {
      return "$";
    }
    String firstPrefix = "$ ";
    String nextPrefix = "  ";
    int firstWidth = availableShellWidth(width, firstPrefix);
    int nextWidth = availableShellWidth(width, nextPrefix);
    List<String> lines = new ArrayList<>();
    StringBuilder currentLine = new StringBuilder();
    int currentWidth = firstWidth;
    for (String token : tokens) {
      if (currentLine.isEmpty()) {
        appendShellToken(currentLine, token, currentWidth, lines);
        continue;
      }
      if (currentLine.length() + 1 + token.length() <= currentWidth) {
        currentLine.append(' ').append(token);
        continue;
      }
      lines.add(currentLine + " \\");
      currentLine.setLength(0);
      currentWidth = nextWidth;
      appendShellToken(currentLine, token, currentWidth, lines);
    }
    lines.add(currentLine.toString());
    StringBuilder rendered = new StringBuilder(firstPrefix).append(lines.getFirst());
    for (int index = 1; index < lines.size(); index++) {
      rendered.append(TEXT_LINE_SEPARATOR).append(nextPrefix).append(lines.get(index));
    }
    return rendered.toString();
  }

  private static void appendShellToken(
      StringBuilder currentLine, String token, int width, List<String> completedLines) {
    List<String> tokenParts = splitLongToken(token, Math.max(1, width));
    for (String tokenPart : tokenParts) {
      if (currentLine.isEmpty()) {
        currentLine.append(tokenPart);
      } else {
        completedLines.add(currentLine + " \\");
        currentLine.setLength(0);
        currentLine.append(tokenPart);
      }
    }
  }

  private static int availableShellWidth(int width, String prefix) {
    return width == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(12, width - prefix.length());
  }

  private static List<String> shellTokens(String command) {
    Matcher matcher = SHELL_TOKEN.matcher(command);
    List<String> tokens = new ArrayList<>();
    while (matcher.find()) {
      tokens.add(matcher.group());
    }
    return List.copyOf(tokens);
  }

  private static int preferredSplitIndex(String token, int start, int width) {
    int limit = Math.min(token.length(), start + width);
    for (int index = limit - 1; index > start; index--) {
      if (WRAP_PREFERRED_BREAKS.indexOf(token.charAt(index)) >= 0) {
        return index + 1;
      }
    }
    return limit;
  }

  private static int[] tableWidths(List<String> headers, List<List<String>> rows) {
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
    return renderSummaryBlock(summary, renderKeyValueBlock(detailRows, maxWidth));
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
