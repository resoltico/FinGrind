package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.WireValue;

/** Public enum fixture whose wireValue() is blank so validation rejects it. */
public enum CliBlankWireValueFixture implements WireValue {
  UNSAFE;

  @Override
  public String wireValue() {
    return " ";
  }
}
