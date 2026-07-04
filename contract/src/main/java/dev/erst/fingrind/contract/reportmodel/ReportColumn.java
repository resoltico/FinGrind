package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.WireValue;
import java.util.Objects;

/** One tabular column definition inside a report section or totals block. */
public record ReportColumn(String key, String title, Alignment alignment) {
  /** Column alignment contract for shared report projection. */
  public enum Alignment implements WireValue {
    LEFT("left"),
    RIGHT("right");

    private final String wireValue;

    Alignment(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }

  /** Validates one report column. */
  public ReportColumn {
    key = ContractDescriptorValidation.requireText(key, "key");
    title = ContractDescriptorValidation.requireText(title, "title");
    Objects.requireNonNull(alignment, "alignment");
  }
}
