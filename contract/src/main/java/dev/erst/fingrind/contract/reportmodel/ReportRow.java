package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** One ordered row of projected report cell values. */
public record ReportRow(String rowId, List<String> cells) {
  /** Validates one report row. */
  public ReportRow {
    rowId = ContractDescriptorValidation.requireText(rowId, "rowId");
    cells = ContractDescriptorValidation.copyList(cells, "cells");
  }
}
