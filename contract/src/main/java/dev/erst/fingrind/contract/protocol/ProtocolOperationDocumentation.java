package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Human-facing documentation facts for one canonical public protocol operation. */
public record ProtocolOperationDocumentation(String analysisSummary, List<String> examples) {
  /** Validates one operation-documentation descriptor. */
  public ProtocolOperationDocumentation {
    analysisSummary = requireText(analysisSummary, "analysisSummary");
    examples = copyList(examples);
  }

  private static String requireText(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName);
    if (value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank.");
    }
    return value;
  }

  private static <T> List<T> copyList(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }
}
