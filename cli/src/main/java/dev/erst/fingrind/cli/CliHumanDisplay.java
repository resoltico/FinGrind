package dev.erst.fingrind.cli;

import java.util.Objects;

/** Shared operator-first display grammar for labels, identifiers, and derived rows. */
final class CliHumanDisplay {
  private static final int OPAQUE_REFERENCE_LENGTH = 8;

  private CliHumanDisplay() {}

  static String accountLabel(String accountCode, String accountName) {
    return Objects.requireNonNull(accountName, "accountName")
        + " ["
        + Objects.requireNonNull(accountCode, "accountCode")
        + "]";
  }

  static String opaqueReference(String rawValue) {
    String value = Objects.requireNonNull(rawValue, "rawValue");
    return value.length() <= OPAQUE_REFERENCE_LENGTH
        ? value
        : value.substring(0, OPAQUE_REFERENCE_LENGTH);
  }

  static String calculatedLineLabel() {
    return "Calculated line";
  }
}
