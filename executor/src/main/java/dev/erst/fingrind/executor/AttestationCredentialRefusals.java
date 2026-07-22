package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.attestation.AttestationAuthorizationLimits;
import java.nio.file.Path;

/** Projects selected-attestation-credential failures consistently at executor boundaries. */
final class AttestationCredentialRefusals {
  private AttestationCredentialRefusals() {}

  static <T> ContractDecision<T> forOperation(Path contextPath) {
    return rejected(contextPath, "this operation.");
  }

  static <T> ContractDecision<T> forReceiptExport(Path bookPath) {
    return rejected(bookPath, "receipt export.");
  }

  private static <T> ContractDecision<T> rejected(Path contextPath, String authorizationScope) {
    return ContractDecision.rejected(
        ContractErrors.Descriptor.INVALID_ATTESTATION_CREDENTIAL.failureAt(
            contextPath,
            "FinGrind could not open the selected attestation credentials.",
            "Provide one through "
                + AttestationAuthorizationLimits.MAXIMUM_QUORUM
                + " readable existing attestation credential triples authorized for "
                + authorizationScope,
            "--attestation-principal-id"));
  }
}
