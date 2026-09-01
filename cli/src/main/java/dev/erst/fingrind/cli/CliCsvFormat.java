package dev.erst.fingrind.cli;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Renders CLI CSV exports with one canonical escaping policy. */
final class CliCsvFormat {
  private static final String CSV_LINE_SEPARATOR = "\n";

  private CliCsvFormat() {}

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

  static int csvFieldCount(String row) {
    return parseRow(row).size();
  }

  static List<String> parseRow(String row) {
    Objects.requireNonNull(row, "row");
    if (row.isEmpty()) {
      return List.of("");
    }
    List<String> fields = new java.util.ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean inQuotes = false;
    int index = 0;
    while (index < row.length()) {
      char current = row.charAt(index);
      if (current == '"') {
        if (inQuotes && index + 1 < row.length() && row.charAt(index + 1) == '"') {
          field.append('"');
          index += 2;
          continue;
        }
        inQuotes = !inQuotes;
        index++;
        continue;
      }
      if (current == ',' && !inQuotes) {
        fields.add(field.toString());
        field.setLength(0);
        index++;
        continue;
      }
      field.append(current);
      index++;
    }
    fields.add(field.toString());
    return List.copyOf(fields);
  }

  private static String csvRow(List<String> values) {
    return values.stream().map(CliCsvFormat::csvCell).collect(Collectors.joining(","));
  }

  private static String csvCell(String value) {
    String spreadsheetSafe = spreadsheetSafe(value);
    String escaped = spreadsheetSafe.replace("\"", "\"\"");
    if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
      return "\"" + escaped + "\"";
    }
    return escaped;
  }

  private static String spreadsheetSafe(String value) {
    String checked = Objects.requireNonNull(value, "value");
    int index = 0;
    while (index < checked.length() && Character.isWhitespace(checked.charAt(index))) {
      index++;
    }
    if (index < checked.length() && "=+-@".indexOf(checked.charAt(index)) >= 0) {
      return "'" + checked;
    }
    return checked;
  }
}
