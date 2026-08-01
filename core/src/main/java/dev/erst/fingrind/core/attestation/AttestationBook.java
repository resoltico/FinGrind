package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/**
 * Immutable protected-book evidence, including malformed candidate chains, presented to the
 * verifier.
 */
final class AttestationBook {
  private final List<AttestationBookOperation> operations;

  AttestationBook(List<AttestationBookOperation> operations) {
    this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
  }

  List<AttestationBookOperation> operations() {
    return operations;
  }
}
