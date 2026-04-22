package dev.erst.fingrind.cli;

/** Public enum fixture whose wireValue() is blank so validation rejects it. */
public enum CliBlankWireValueFixture {
  UNSAFE;

  public String wireValue() {
    return " ";
  }
}
