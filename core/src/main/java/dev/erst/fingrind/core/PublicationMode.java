package dev.erst.fingrind.core;

/** Defines how a staged artifact may become its final publication member. */
public enum PublicationMode {
  NO_REPLACE_LINK("no-replace-link"),
  REPLACE("replace");

  private final String wireValue;

  PublicationMode(String wireValue) {
    this.wireValue = wireValue;
  }

  String wireValue() {
    return wireValue;
  }

  static PublicationMode fromWireValue(String wireValue) {
    for (PublicationMode value : values()) {
      if (value.wireValue.equals(wireValue)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Unsupported publication mode: " + wireValue);
  }
}
