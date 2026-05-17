package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** First-class tax profile for one accounting entity book. */
public record TaxProfile(
    List<TaxRegistration> registrations, List<TaxCodeDefinition> taxCodeDefinitions) {
  /** Defensively copies one tax profile. */
  public TaxProfile {
    registrations = List.copyOf(Objects.requireNonNull(registrations, "registrations"));
    taxCodeDefinitions =
        List.copyOf(Objects.requireNonNull(taxCodeDefinitions, "taxCodeDefinitions"));
  }

  /** Returns the canonical empty tax profile. */
  public static TaxProfile empty() {
    return new TaxProfile(List.of(), List.of());
  }
}
