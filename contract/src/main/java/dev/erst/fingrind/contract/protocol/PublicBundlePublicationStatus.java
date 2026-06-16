package dev.erst.fingrind.contract.protocol;

import java.util.Arrays;
import java.util.Objects;

/** Explicit publication states for canonical public CLI bundle targets. */
public enum PublicBundlePublicationStatus {
  PUBLISHED("published"),
  NOT_PUBLISHED("not-published");

  private final String wireValue;

  PublicBundlePublicationStatus(String wireValue) {
    this.wireValue = wireValue;
  }

  /** Canonical wire value for this publication status. */
  public String wireValue() {
    return wireValue;
  }

  /** Parses one canonical wire value into the publication-status enum. */
  public static PublicBundlePublicationStatus fromWireValue(String wireValue) {
    String normalized = Objects.requireNonNull(wireValue, "wireValue").strip();
    return Arrays.stream(values())
        .filter(status -> status.wireValue.equals(normalized))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unsupported bundle publication status: " + normalized + "."));
  }
}
