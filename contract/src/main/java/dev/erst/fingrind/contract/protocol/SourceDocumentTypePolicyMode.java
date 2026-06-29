package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Describes how one entry kind constrains caller-authored source-document types. */
public enum SourceDocumentTypePolicyMode implements WireValue {
  ENUMERATED,
  PATTERN_ONLY;

  /** Returns the stable wire value for this policy mode. */
  @Override
  public String wireValue() {
    return switch (this) {
      case ENUMERATED -> "enumerated";
      case PATTERN_ONLY -> "pattern-only";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(SourceDocumentTypePolicyMode.class);
  }

  /** Parses one published source-document-type policy mode. */
  public static SourceDocumentTypePolicyMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        SourceDocumentTypePolicyMode.class, wireValue, "Unsupported source document type policy");
  }
}
