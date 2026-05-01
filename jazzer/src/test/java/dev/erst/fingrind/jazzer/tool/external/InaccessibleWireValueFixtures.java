package dev.erst.fingrind.jazzer.tool.external;

import dev.erst.fingrind.core.WireValue;
import org.jspecify.annotations.NullMarked;

/** Exposes package-private external wire-value fixtures to neighboring Jazzer tests. */
@NullMarked
public final class InaccessibleWireValueFixtures {
  private InaccessibleWireValueFixtures() {}

  public static Class<?> inaccessibleWireValueEnumClass() {
    return InaccessibleWireValueEnum.class;
  }

  public static Class<? extends WireValue> inaccessibleWireValueTypeClass() {
    return InaccessibleWireValueType.class;
  }
}
