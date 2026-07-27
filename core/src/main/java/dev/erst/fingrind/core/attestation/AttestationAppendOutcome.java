package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/**
 * Exact durable outcome of admitting one requested attestation operation.
 *
 * <p>An authenticated direct operation can be appended immediately or found to be an
 * already-present exact replay. Aggregate-plan child dispositions are intentionally modeled by
 * plan-only capability outcomes, never by this direct-attestation result.
 */
public sealed interface AttestationAppendOutcome
    permits AttestationAppendOutcome.Appended, AttestationAppendOutcome.AlreadyPresent {
  /** Returns the exact new append or rejects an outcome that did not append an operation. */
  default Appended requireAppended() {
    return switch (this) {
      case Appended appended -> appended;
      case AlreadyPresent _ ->
          throw new IllegalStateException(
              "An already-present attestation operation has no newly appended verification.");
    };
  }

  /** Returns the verified append or rejects an outcome that did not append an operation. */
  default AttestationVerification requireVerifiedAppend() {
    return requireAppended().verification();
  }

  /** One newly appended, fully verified attestation operation. */
  record Appended(AttestationVerification verification) implements AttestationAppendOutcome {
    public Appended {
      Objects.requireNonNull(verification, "verification");
    }
  }

  /** One exact idempotent replay whose attestation operation already exists. */
  enum AlreadyPresent implements AttestationAppendOutcome {
    /** The only already-present outcome value. */
    INSTANCE
  }
}
