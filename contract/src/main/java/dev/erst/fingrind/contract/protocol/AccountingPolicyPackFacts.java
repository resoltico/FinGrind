package dev.erst.fingrind.contract.protocol;

import java.util.List;
import java.util.Objects;

/** Structured public facts for one executable accounting-policy pack. */
public record AccountingPolicyPackFacts(
    String policyPackId,
    String displayName,
    AccountingBaselineTarget targetBaseline,
    List<String> supportedEntityForms,
    List<PolicyDimensionFacts> policyDimensions,
    String description) {
  /** Validates one published accounting-policy-pack fact family. */
  public AccountingPolicyPackFacts {
    Objects.requireNonNull(policyPackId, "policyPackId");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(targetBaseline, "targetBaseline");
    supportedEntityForms =
        List.copyOf(Objects.requireNonNull(supportedEntityForms, "supportedEntityForms"));
    policyDimensions = List.copyOf(Objects.requireNonNull(policyDimensions, "policyDimensions"));
    Objects.requireNonNull(description, "description");
  }
}
