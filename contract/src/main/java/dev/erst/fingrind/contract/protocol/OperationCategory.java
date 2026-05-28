package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.WireValue;
import java.util.List;
import java.util.Objects;

/** Top-level operation groups published by the FinGrind protocol catalog. */
public enum OperationCategory implements WireValue {
  /** Discovery operations that do not access a book. */
  DISCOVERY("discovery"),
  /** Book administration operations that mutate lifecycle or account-registry state. */
  ADMINISTRATION("administration"),
  /** Read-side operations that inspect book state without mutating it. */
  QUERY("query"),
  /** Write-side posting operations. */
  WRITE("write");

  private final String wireValue;

  OperationCategory(String wireValue) {
    this.wireValue = Objects.requireNonNull(wireValue, "wireValue");
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  /** Lists every stable wire token published for top-level operation categories. */
  public static List<String> wireValues() {
    return WireValue.wireValues(OperationCategory.class);
  }

  /** Resolves one published operation-category token back to the canonical enum member. */
  public static OperationCategory fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        OperationCategory.class, wireValue, "Unsupported operation category");
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
