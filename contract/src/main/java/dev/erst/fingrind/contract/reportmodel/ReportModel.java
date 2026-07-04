package dev.erst.fingrind.contract.reportmodel;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessPayload;
import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Shared report content model projected across JSON, text, CSV, and PDF outputs. */
public record ReportModel(
    String family,
    String title,
    Orientation orientation,
    ReportContext context,
    List<ReportVerdict> verdicts,
    List<ReportSection> sections)
    implements ProtocolSuccessPayload {
  /** Page orientation contract for projected report artifacts. */
  public enum Orientation implements WireValue {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    private final String wireValue;

    Orientation(String wireValue) {
      this.wireValue = wireValue;
    }

    @Override
    public String wireValue() {
      return wireValue;
    }
  }

  /** Validates one shared report model. */
  public ReportModel {
    family = ContractDescriptorValidation.requireText(family, "family");
    title = ContractDescriptorValidation.requireText(title, "title");
    Objects.requireNonNull(orientation, "orientation");
    Objects.requireNonNull(context, "context");
    verdicts = ContractDescriptorValidation.copyList(verdicts, "verdicts");
    sections = ContractDescriptorValidation.copyList(sections, "sections");
  }
}
