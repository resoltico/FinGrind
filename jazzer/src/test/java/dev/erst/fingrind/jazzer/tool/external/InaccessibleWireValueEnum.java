package dev.erst.fingrind.jazzer.tool.external;

import dev.erst.fingrind.core.WireValue;
import org.jspecify.annotations.NullMarked;

@NullMarked
enum InaccessibleWireValueEnum implements WireValue {
  ALPHA("alpha");

  private final String wireValue;

  InaccessibleWireValueEnum(String wireValue) {
    this.wireValue = wireValue;
  }

  @Override
  public String wireValue() {
    return wireValue;
  }

  public static InaccessibleWireValueEnum fromWireValue(String wireValue) {
    return ALPHA;
  }
}
