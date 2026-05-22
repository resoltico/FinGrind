package dev.erst.fingrind.contract.protocol;

import java.util.Objects;

/** Structured public facts for one executable persisted accounting policy profile. */
public record AccountingPolicyProfileFacts(
    String profileId, String displayName, String description) {
  /** Validates one published accounting-policy-profile fact family. */
  public AccountingPolicyProfileFacts {
    Objects.requireNonNull(profileId, "profileId");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(description, "description");
  }
}
