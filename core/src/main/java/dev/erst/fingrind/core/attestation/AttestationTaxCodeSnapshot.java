package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** One complete semantic tax-code definition owned by a tax-registration declaration. */
public record AttestationTaxCodeSnapshot(
    String taxCode,
    String taxCodeName,
    int ratePartsPerMillionOfWhole,
    String inclusionMode,
    String applicationKind) {
  /** Requires the normalized domain values that the fixed tax-code preimage fields commit. */
  public AttestationTaxCodeSnapshot {
    requireText(taxCode, "taxCode");
    requireText(taxCodeName, "taxCodeName");
    if (ratePartsPerMillionOfWhole < 0 || ratePartsPerMillionOfWhole > 1_000_000) {
      throw new IllegalArgumentException(
          "ratePartsPerMillionOfWhole must be between 0 and 1000000.");
    }
    requireText(inclusionMode, "inclusionMode");
    requireText(applicationKind, "applicationKind");
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
  }
}
