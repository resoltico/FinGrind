package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationAuthorizationFailure;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Objects;

/** Local outcome for one attested credential-registry or authorization-policy mutation. */
public sealed interface ProtectedBookRegistryMutationOutcome
    permits ProtectedBookRegistryMutationOutcome.Mutated,
        ProtectedBookRegistryMutationOutcome.Rejected,
        ProtectedBookRegistryMutationOutcome.AuthorizationRejected {

  /** Successfully appended one immutable authority-changing operation. */
  record Mutated(
      Path bookFilePath, String operationKind, BigInteger headOrder, String operationHeadHex)
      implements ProtectedBookRegistryMutationOutcome {
    public Mutated {
      bookFilePath =
          Objects.requireNonNull(bookFilePath, "bookFilePath").toAbsolutePath().normalize();
      Objects.requireNonNull(operationKind, "operationKind");
      Objects.requireNonNull(headOrder, "headOrder");
      operationHeadHex = requireOperationHeadHex(operationHeadHex);
    }

    private static String requireOperationHeadHex(String value) {
      String checked = Objects.requireNonNull(value, "operationHeadHex");
      if (!checked.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException(
            "operationHeadHex must contain 64 lowercase hexadecimal characters.");
      }
      return checked;
    }
  }

  /** Deterministic refusal before an authority-changing operation reaches admission. */
  record Rejected(ProtectedBookMaintenanceRejection rejection)
      implements ProtectedBookRegistryMutationOutcome {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  /** Live-head authorization refused the signed mutation before any durable write. */
  record AuthorizationRejected(AttestationAuthorizationFailure failure)
      implements ProtectedBookRegistryMutationOutcome {
    public AuthorizationRejected {
      Objects.requireNonNull(failure, "failure");
    }
  }
}
