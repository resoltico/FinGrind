package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationRegistryInspection;
import java.time.Instant;
import java.util.Objects;

/** Closed result family for explicit book initialization. */
public sealed interface OpenBookResult permits OpenBookResult.Opened, OpenBookResult.Rejected {

  /** Success result for a newly initialized book. */
  record Opened(
      Instant initializedAt,
      BookIdentity bookIdentity,
      AttestationRegistryInspection attestationTrustRoot)
      implements OpenBookResult {
    /** Validates the initialization timestamp. */
    public Opened {
      Objects.requireNonNull(initializedAt, "initializedAt");
      Objects.requireNonNull(bookIdentity, "bookIdentity");
      Objects.requireNonNull(attestationTrustRoot, "attestationTrustRoot");
    }
  }

  /** Deterministic refusal for open-book. */
  record Rejected(BookAdministrationRejection rejection) implements OpenBookResult {
    /** Validates the deterministic rejection. */
    public Rejected {
      Objects.requireNonNull(rejection, "rejection");
    }
  }
}
