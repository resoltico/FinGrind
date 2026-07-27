package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.ProtocolOptions;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Enforces the credential-selection contract that depends on a decoded ledger plan's meaning. */
final class CliExecutePlanAttestationPolicy {
  private CliExecutePlanAttestationPolicy() {}

  /**
   * Returns the deterministic refusal for an invalid plan/credential pairing, or {@code null} when
   * the caller may proceed to the workflow boundary.
   */
  static @Nullable ContractFailure refusalFor(BookAccess bookAccess, LedgerPlan plan) {
    Objects.requireNonNull(bookAccess, "bookAccess");
    Objects.requireNonNull(plan, "plan");
    boolean credentialsSelected = !bookAccess.attestationCredentialSources().isEmpty();
    if (plan.containsBookMutation()) {
      return credentialsSelected
          ? null
          : CliAttestationCredentialFailures.missingMutationCredentials(bookAccess);
    }
    if (!credentialsSelected) {
      return null;
    }
    return ContractErrors.Descriptor.ATTESTATION_CREDENTIALS_NOT_ALLOWED.failure(
        "A query-only or assertion-only ledger plan must not receive attestation credentials.",
        "Remove "
            + ProtocolOptions.Attestation.CUSTODIAN
            + " and its aligned credential arguments. Supply them only when the plan contains a mutating step.",
        ProtocolOptions.Attestation.CUSTODIAN);
  }
}
