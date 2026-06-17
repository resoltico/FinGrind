package dev.erst.fingrind.contract.protocol;

import java.util.List;

/** Describes how one entry kind constrains caller-authored source-document types. */
public enum SourceDocumentTypePolicyMode {
  ENUMERATED,
  PATTERN_ONLY;

  /** Returns the stable wire value for this policy mode. */
  public String wireValue() {
    return switch (this) {
      case ENUMERATED -> "enumerated";
      case PATTERN_ONLY -> "pattern-only";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return List.of(ENUMERATED.wireValue(), PATTERN_ONLY.wireValue());
  }
}
