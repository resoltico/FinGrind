package dev.erst.fingrind.jazzer.tool.external;

import dev.erst.fingrind.core.WireValue;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class InaccessibleWireValueType implements WireValue {
  private static final InaccessibleWireValueType ALPHA = new InaccessibleWireValueType("alpha");

  private final String wireValue;

  private InaccessibleWireValueType(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  public static InaccessibleWireValueType fromWireValue(String wireValue) {
    java.util.Objects.requireNonNull(wireValue);
    return ALPHA;
  }
}
