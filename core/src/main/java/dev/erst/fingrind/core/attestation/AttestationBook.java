package dev.erst.fingrind.core.attestation;

import java.util.List;
import java.util.Objects;

/** Immutable protected-book evidence presented to the pure verifier. */
final class AttestationBook {
  private final List<AttestationBookOperation> operations;

  AttestationBook(List<AttestationBookOperation> operations) {
    this.operations = List.copyOf(Objects.requireNonNull(operations, "operations"));
    if (this.operations.isEmpty()) {
      throw new IllegalArgumentException("An attested book must contain its genesis operation.");
    }
  }

  List<AttestationBookOperation> operations() {
    return operations;
  }
}
