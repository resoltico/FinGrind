package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.WireValue;

/** Public enum fixture whose wireValue() can throw to exercise wrapped failures. */
public enum CliExplodingWireValueFixture implements WireValue {
  UNSAFE,
  SAFE;

  @Override
  public String wireValue() {
    return switch (this) {
      case UNSAFE -> throw new UnsupportedOperationException("boom");
      case SAFE -> "safe";
    };
  }
}
