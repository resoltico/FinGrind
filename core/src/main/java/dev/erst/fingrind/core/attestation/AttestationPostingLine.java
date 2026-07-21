package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** One canonical journal line committed by an attested posting operation. */
public record AttestationPostingLine(
    String accountCode, String side, String currencyCode, long minorUnits) {
  /** Requires one complete non-negative journal-line commitment. */
  public AttestationPostingLine {
    requireText(accountCode, "accountCode");
    requireText(side, "side");
    requireText(currencyCode, "currencyCode");
    if (minorUnits <= 0) {
      throw new IllegalArgumentException("minorUnits must be positive.");
    }
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
  }
}
