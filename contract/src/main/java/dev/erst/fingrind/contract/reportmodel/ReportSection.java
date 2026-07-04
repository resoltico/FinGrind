package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** One titled section in the shared report content model. */
public record ReportSection(
    String key,
    String title,
    List<ReportVerdict> verdicts,
    List<ReportColumn> columns,
    List<ReportRow> rows,
    List<ReportTotals> totals) {
  /** Validates one report section. */
  public ReportSection {
    key = ContractDescriptorValidation.requireText(key, "key");
    title = ContractDescriptorValidation.requireText(title, "title");
    verdicts = ContractDescriptorValidation.copyList(verdicts, "verdicts");
    columns = ContractDescriptorValidation.copyList(columns, "columns");
    rows = ContractDescriptorValidation.copyList(rows, "rows");
    totals = ContractDescriptorValidation.copyList(totals, "totals");
    ReportModelSupport.requireCellWidth(columns, rows, "rows");
  }
}
