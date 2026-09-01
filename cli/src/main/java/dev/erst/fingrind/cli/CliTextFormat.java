package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.Money;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Shared text-table, CSV, and money-display formatting for CLI query output. */
final class CliTextFormat {
  private CliTextFormat() {}

  static String renderKeyValueBlock(List<List<String>> rows) {
    return CliTextKeyValueBlockFormat.renderKeyValueBlock(rows);
  }

  static String renderKeyValueBlock(List<List<String>> rows, int totalWidth) {
    return CliTextKeyValueBlockFormat.renderKeyValueBlock(rows, totalWidth);
  }

  static String renderKeyValueBlock(List<List<String>> rows, int totalWidth, int labelWidthCap) {
    return CliTextKeyValueBlockFormat.renderKeyValueBlock(rows, totalWidth, labelWidthCap);
  }

  static String renderTitledBlock(String title, String body) {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    String safeTitle = CliTextSafety.visible(title);
    return safeTitle + "\n" + "=".repeat(CliTerminalWidth.cells(safeTitle)) + "\n" + "\n" + body;
  }

  static String renderSummaryBlock(String summary, String body) {
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(body, "body");
    String safeSummary = CliTextSafety.visible(summary);
    return safeSummary + "\n" + "-".repeat(CliTerminalWidth.cells(safeSummary)) + "\n" + body;
  }

  static String renderTable(
      List<String> headers, List<List<String>> rows, int... rightAlignedColumns) {
    return CliTextTableFormat.renderTable(headers, rows, rightAlignedColumns);
  }

  static String renderAdaptiveTable(
      int maxWidth, List<String> headers, List<List<String>> rows, int... rightAlignedColumns) {
    return CliTextTableFormat.renderAdaptiveTable(maxWidth, headers, rows, rightAlignedColumns);
  }

  static String renderCsv(List<String> headers, List<List<String>> rows) {
    return CliCsvFormat.renderCsv(headers, rows);
  }

  static String displayMoney(Money money) {
    Objects.requireNonNull(money, "money");
    return money.canonicalDecimal();
  }

  static String joined(List<String> values) {
    return values.stream().filter(value -> !value.isBlank()).collect(Collectors.joining(", "));
  }

  static String wrap(String text, int width) {
    return CliTextWrap.wrap(text, width);
  }

  static String wrapLineBlock(List<String> lines, int width) {
    return CliTextWrap.wrapLineBlock(lines, width);
  }

  static String renderBulletedBlock(List<String> items, int width) {
    return CliTextWrap.renderBulletedBlock(items, width);
  }

  static String renderLiteralBlock(List<String> lines, String prefix) {
    return CliTextWrap.renderLiteralBlock(lines, prefix);
  }

  static String renderShellCommandBlock(List<String> commands, int width) {
    return CliShellCommandFormat.renderShellCommandBlock(commands, width);
  }
}
