package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** One titled totals block inside a report section. */
public record ReportTotals(
    String key, String title, List<ReportColumn> columns, List<ReportRow> rows) {
  /** Validates one report totals block. */
  public ReportTotals {
    key = ContractDescriptorValidation.requireText(key, "key");
    title = ContractDescriptorValidation.requireText(title, "title");
    columns = ContractDescriptorValidation.copyList(columns, "columns");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    ReportModelSupport.requireCellWidth(columns, rows, "rows");
  }
}
