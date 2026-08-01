package dev.erst.fingrind.executor.maintenance;

import dev.erst.fingrind.core.attestation.AttestationAppendOutcome;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import java.util.Objects;

/** Enforces that lifecycle mutations publish a newly appended attestation operation. */
final class AttestedProtectedBookAppendOutcomes {
  private AttestedProtectedBookAppendOutcomes() {}

  static AttestationAppendOutcome.Appended requireNewAppend(
      AttestationAppendOutcome outcome, AttestationOperationKind operationKind) {
    return switch (Objects.requireNonNull(outcome, "outcome")) {
      case AttestationAppendOutcome.Appended appended -> appended;
      case AttestationAppendOutcome.AlreadyPresent _ ->
          throw new IllegalStateException(
              operationKind.wireToken() + " cannot reuse an existing attestation operation.");
    };
  }
}
