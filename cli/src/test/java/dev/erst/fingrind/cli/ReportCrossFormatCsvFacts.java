package dev.erst.fingrind.cli;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Extracts cross-format fact sets from the CSV projector output. */
@SuppressWarnings("PMD.UseConcurrentHashMap")
final class ReportCrossFormatCsvFacts {
  private ReportCrossFormatCsvFacts() {}

  static Set<String> fromCsv(String csv) {
    List<String> lines = csv.lines().toList();
    List<String> headers = CliCsvFormat.parseRow(lines.getFirst());
    Set<String> facts = new LinkedHashSet<>();
    Map<BlockKey, BlockState> blockStates = new java.util.LinkedHashMap<>();
    int index = 1;
    while (index < lines.size()) {
      List<String> row = CliCsvFormat.parseRow(lines.get(index));
      String sectionKey = requiredValue(headers, row, "sectionKey");
      String sectionTitle = requiredValue(headers, row, "sectionTitle");
      String blockKind = requiredValue(headers, row, "blockKind");
      if ("verdict".equals(blockKind) || "context".equals(blockKind)) {
        index = appendVerdictFacts(facts, headers, lines, index, sectionKey, sectionTitle);
        continue;
      }
      appendTabularFacts(facts, headers, row, sectionKey, sectionTitle, blockKind, blockStates);
      index++;
    }
    return facts;
  }

  private static int appendVerdictFacts(
      Set<String> facts,
      List<String> headers,
      List<String> lines,
      int index,
      String sectionKey,
      String sectionTitle) {
    if (index + 1 >= lines.size()) {
      throw new IllegalStateException("Missing verdict value row for section: " + sectionKey);
    }
    List<String> firstRow = CliCsvFormat.parseRow(lines.get(index));
    List<String> secondRow = CliCsvFormat.parseRow(lines.get(index + 1));
    String rowId = requiredValue(headers, firstRow, "rowId");
    String secondRowId = requiredValue(headers, secondRow, "rowId");
    if (!rowId.equals(secondRowId)) {
      throw new IllegalStateException(
          "Mismatched verdict row ids: " + rowId + " vs " + secondRowId);
    }
    if (!"summary".equals(sectionKey) && !"context".equals(sectionKey)) {
      facts.add(ReportCrossFormatStructuredFacts.sectionTitleFact(sectionKey, sectionTitle));
    }
    facts.add(
        ReportCrossFormatStructuredFacts.verdictFact(
            sectionKey,
            pairedValue(headers, firstRow, secondRow, "label"),
            pairedValue(headers, firstRow, secondRow, "value")));
    return index + 2;
  }

  private static void appendTabularFacts(
      Set<String> facts,
      List<String> headers,
      List<String> row,
      String sectionKey,
      String sectionTitle,
      String blockKind,
      Map<BlockKey, BlockState> blockStates) {
    String blockTitle = requiredValue(headers, row, "blockTitle");
    String factBlockTitle = "table".equals(blockKind) ? "table" : blockTitle;
    String columnKey = requiredValue(headers, row, "columnKey");
    String columnTitle = requiredValue(headers, row, "columnTitle");
    String rowId = requiredValue(headers, row, "rowId");
    BlockState blockState =
        blockStates.computeIfAbsent(
            new BlockKey(sectionKey, factBlockTitle), ignored -> new BlockState());
    facts.add(ReportCrossFormatStructuredFacts.sectionTitleFact(sectionKey, sectionTitle));
    if ("totals".equals(blockKind)) {
      facts.add(ReportCrossFormatStructuredFacts.totalsTitleFact(sectionKey, blockTitle));
    }
    int columnIndex = blockState.columnIndex(columnKey);
    facts.add(
        ReportCrossFormatStructuredFacts.columnFact(
            sectionKey, factBlockTitle, columnIndex, columnKey, columnTitle));
    facts.add(
        ReportCrossFormatStructuredFacts.cellFact(
            sectionKey,
            factBlockTitle,
            blockState.rowIndex(rowId),
            columnIndex,
            columnKey,
            requiredValue(headers, row, "value")));
  }

  private static String pairedValue(
      List<String> headers, List<String> leftRow, List<String> rightRow, String columnKey) {
    if (columnKey.equals(requiredValue(headers, leftRow, "columnKey"))) {
      return requiredValue(headers, leftRow, "value");
    }
    if (columnKey.equals(requiredValue(headers, rightRow, "columnKey"))) {
      return requiredValue(headers, rightRow, "value");
    }
    throw new IllegalStateException("Missing paired verdict column: " + columnKey);
  }

  private static String requiredValue(List<String> headers, List<String> row, String key) {
    int index = headers.indexOf(key);
    if (index < 0 || index >= row.size()) {
      throw new IllegalStateException("Missing CSV value for key: " + key);
    }
    return row.get(index);
  }

  private record BlockKey(String sectionKey, String blockTitle) {}

  /** Tracks stable row and column order while CSV rows are read sequentially. */
  private static final class BlockState {
    private final Map<String, Integer> rowIndexes = new java.util.LinkedHashMap<>();
    private final Map<String, Integer> columnIndexes = new java.util.LinkedHashMap<>();

    private BlockState() {}

    private int rowIndex(String rowId) {
      return rowIndexes.computeIfAbsent(rowId, ignored -> rowIndexes.size());
    }

    private int columnIndex(String columnKey) {
      return columnIndexes.computeIfAbsent(columnKey, ignored -> columnIndexes.size());
    }
  }
}
