package dev.erst.fingrind.contract.bookkeeping;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.Objects;

/** Published result family for one credential-registry or authorization-policy mutation. */
public sealed interface AttestationRegistryMutationResult
    permits AttestationRegistryMutationResult.Mutated,
        AttestationRegistryMutationResult.Rejected,
        AttestationRegistryMutationResult.AuthorizationRejected {

  /** One immutable authority-changing operation appended to the selected book. */
  record Mutated(
      Path bookFilePath, String operationKind, BigInteger headOrder, String operationHeadHex)
      implements AttestationRegistryMutationResult {
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
  record Rejected(BookMaintenanceRejection rejection) implements AttestationRegistryMutationResult {
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }

  /** The live historical attestation policy refused the signed mutation. */
  record AuthorizationRejected(AttestationVerificationFailure failure)
      implements AttestationRegistryMutationResult {
    public AuthorizationRejected {
      Objects.requireNonNull(failure, "failure");
    }
  }
}
