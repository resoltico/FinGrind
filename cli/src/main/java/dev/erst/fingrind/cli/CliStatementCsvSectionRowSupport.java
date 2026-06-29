package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.CurrencyBalance;
import java.util.ArrayList;
import java.util.List;

/** Shared builders for statement CSV section summary rows. */
final class CliStatementCsvSectionRowSupport {
  private CliStatementCsvSectionRowSupport() {}

  static List<String> valuedRow(
      StatementRowSpec spec, String relationKind, String lineKind, CurrencyBalance balance) {
    List<String> row = prefixRow(spec, relationKind);
    row.addAll(spec.detailColumns());
    row.add(lineKind);
    row.add(balance.netAmount().currencyUnit().code());
    row.add(CliQueryScopeText.displayMoney(balance.debitTotal()));
    row.add(CliQueryScopeText.displayMoney(balance.creditTotal()));
    row.add(CliQueryScopeText.displayMoney(balance.netAmount()));
    row.add(balance.balanceSide().wireValue());
    row.add("");
    return List.copyOf(row);
  }

  static List<String> emptyRow(StatementRowSpec spec, String currencyCode, String message) {
    List<String> row = prefixRow(spec, "section-empty");
    row.addAll(spec.detailColumns());
    row.add("");
    row.add(currencyCode);
    row.add("");
    row.add("");
    row.add("");
    row.add("");
    row.add(message);
    return List.copyOf(row);
  }

  private static List<String> prefixRow(StatementRowSpec spec, String relationKind) {
    List<String> row = new ArrayList<>();
    row.add(spec.exportFamily());
    row.add(spec.rowId());
    row.add(spec.parentRowId());
    row.add(relationKind);
    row.add(spec.reportBasis());
    row.add(spec.recordKind());
    row.add(spec.effectiveDateFrom());
    row.add(spec.effectiveDateTo());
    row.add(spec.sectionCode());
    row.add(spec.lineCode());
    row.add(spec.lineName());
    return row;
  }

  record StatementRowSpec(
      String exportFamily,
      String rowId,
      String parentRowId,
      String reportBasis,
      String recordKind,
      String effectiveDateFrom,
      String effectiveDateTo,
      String sectionCode,
      String lineCode,
      String lineName,
      List<String> detailColumns) {
    StatementRowSpec {
      detailColumns = List.copyOf(detailColumns);
    }
  }
}
