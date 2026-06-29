package dev.erst.fingrind.cli;

import java.util.Objects;

/** Shared operator-first display grammar for labels, identifiers, and derived rows. */
final class CliHumanDisplay {
  private CliHumanDisplay() {}

  static String accountLabel(String accountCode, String accountName) {
    return Objects.requireNonNull(accountName, "accountName")
        + " ["
        + Objects.requireNonNull(accountCode, "accountCode")
        + "]";
  }

  static String opaqueReference(String rawValue) {
    return Objects.requireNonNull(rawValue, "rawValue");
  }

  static String calculatedLineLabel() {
    return "Calculated line";
  }
}
