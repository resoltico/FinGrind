package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Stable user-facing diagnostics identifiers emitted on the auxiliary CLI stream. */
public enum ProtocolDiagnosticCode implements WireValue {
  PDF_EXPORTED("pdf-exported"),
  PDF_EXPORT_WARNING("pdf-export-warning");

  private final String wireValue;

  ProtocolDiagnosticCode(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  /** Returns the stable wire value for this diagnostics code. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Returns every stable diagnostics identifier in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProtocolDiagnosticCode.class);
  }

  /** Parses one stable diagnostics identifier. */
  public static ProtocolDiagnosticCode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ProtocolDiagnosticCode.class, wireValue, "Unsupported diagnostics code");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
