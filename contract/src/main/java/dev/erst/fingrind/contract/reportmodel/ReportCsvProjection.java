package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Machine-readable tabular CSV content owned by one report model. */
public record ReportCsvProjection(List<String> headers, List<List<String>> rows) {
  /** Validates one tabular CSV projection without permitting parallel header meanings. */
  public ReportCsvProjection {
    headers = copyHeaders(headers);
    rows = copyRows(rows, headers.size());
  }

  private static List<String> copyHeaders(List<String> headers) {
    List<String> copied =
        new ArrayList<>(ContractDescriptorValidation.copyList(headers, "headers"));
    if (copied.isEmpty()) {
      throw new IllegalArgumentException("headers must not be empty.");
    }
    Set<String> seen = new HashSet<>();
    for (int index = 0; index < copied.size(); index++) {
      String header =
          ContractDescriptorValidation.requireText(copied.get(index), "headers[" + index + "]");
      if (!seen.add(header)) {
        throw new IllegalArgumentException("headers must not contain duplicates: " + header + ".");
      }
      copied.set(index, header);
    }
    return List.copyOf(copied);
  }

  private static List<List<String>> copyRows(List<List<String>> rows, int headerCount) {
    List<List<String>> copiedRows =
        new ArrayList<>(ContractDescriptorValidation.copyList(rows, "rows"));
    for (int index = 0; index < copiedRows.size(); index++) {
      List<String> row =
          ContractDescriptorValidation.copyList(copiedRows.get(index), "rows[" + index + "]");
      if (row.size() != headerCount) {
        throw new IllegalArgumentException(
            "rows[" + index + "] must have " + headerCount + " cells but had " + row.size() + ".");
      }
      copiedRows.set(index, row);
    }
    return List.copyOf(copiedRows);
  }
}
