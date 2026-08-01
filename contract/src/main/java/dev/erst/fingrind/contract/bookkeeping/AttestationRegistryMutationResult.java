package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.runtime.AttestationDiagnosticDescriptors.AdmissionContext;
import java.nio.file.Path;
import java.util.Objects;

/** Published result family for one credential-registry or authorization-policy mutation. */
public sealed interface AttestationRegistryMutationResult
    permits AttestationRegistryMutationResult.Mutated,
        AttestationRegistryMutationResult.Rejected,
        AttestationRegistryMutationResult.AuthorizationRejected {

  /** One immutable authority-changing operation appended to the selected book. */
  record Mutated(Path bookFilePath, String operationKind, AttestationCommit attestationCommit)
      implements AttestationRegistryMutationResult {
    public Mutated {
      bookFilePath =
          Objects.requireNonNull(bookFilePath, "bookFilePath").toAbsolutePath().normalize();
      Objects.requireNonNull(operationKind, "operationKind");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  /** Deterministic refusal before an authority-changing operation reaches admission. */
  record Rejected(BookMaintenanceRejection rejection) implements AttestationRegistryMutationResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  /** The reconstructed current-head attestation policy refused the signed mutation. */
  record AuthorizationRejected(AttestationVerificationFailure failure)
      implements AttestationRegistryMutationResult {
    public AuthorizationRejected {
      failure =
          AttestationVerificationFailure.requireAdmissionFailure(
              failure, AdmissionContext.REGISTRY_MUTATION);
    }
  }
}
