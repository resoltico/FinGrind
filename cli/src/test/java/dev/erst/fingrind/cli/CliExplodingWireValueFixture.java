package dev.erst.fingrind.cli;

/** Public enum fixture whose wireValue() can throw to exercise wrapped failures. */
public enum CliExplodingWireValueFixture {
  UNSAFE,
  SAFE;

  public String wireValue() {
    return switch (this) {
      case UNSAFE -> throw new UnsupportedOperationException("boom");
      case SAFE -> "safe";
    };
  }
}
