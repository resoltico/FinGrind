package dev.erst.fingrind.cli;

/** Public enum fixture whose wireValue() is not a string so validation rejects it. */
public enum CliNonStringWireValueFixture {
  UNSAFE;

  public Object wireValue() {
    return Integer.valueOf(7);
  }
}
