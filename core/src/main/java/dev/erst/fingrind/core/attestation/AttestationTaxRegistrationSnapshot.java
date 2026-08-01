package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** The complete semantic tax-registration state relevant to one declaration operation. */
public record AttestationTaxRegistrationSnapshot(
    String registrationId,
    String registrationName,
    String jurisdiction,
    @Nullable String registrationNumber,
    String payableAccountCode,
    String receivableAccountCode,
    String obligationFrequency,
    int dueDaysAfterPeriodEnd,
    List<AttestationTaxCodeSnapshot> taxCodes) {
  /** Defensively owns all required tax-registration state. */
  public AttestationTaxRegistrationSnapshot {
    requireText(registrationId, "registrationId");
    requireText(registrationName, "registrationName");
    requireText(jurisdiction, "jurisdiction");
    if (registrationNumber != null && registrationNumber.isBlank()) {
      throw new IllegalArgumentException("registrationNumber must not be blank when present.");
    }
    requireText(payableAccountCode, "payableAccountCode");
    requireText(receivableAccountCode, "receivableAccountCode");
    requireText(obligationFrequency, "obligationFrequency");
    if (dueDaysAfterPeriodEnd < 0 || dueDaysAfterPeriodEnd > 366) {
      throw new IllegalArgumentException("dueDaysAfterPeriodEnd must be between 0 and 366.");
    }
    taxCodes = List.copyOf(Objects.requireNonNull(taxCodes, "taxCodes"));
    if (taxCodes.isEmpty()) {
      throw new IllegalArgumentException("taxCodes must not be empty.");
    }
  }

  private static void requireText(String value, String name) {
    if (Objects.requireNonNull(value, name).isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank.");
    }
  }
}
