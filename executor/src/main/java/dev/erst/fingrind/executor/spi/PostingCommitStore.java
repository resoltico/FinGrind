package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;

/** Commits one admissible posting into durable storage. */
@FunctionalInterface
public interface PostingCommitStore {
  /** Attempts one durable commit and returns the ordinary application outcome explicitly. */
  PostingCommitResult commit(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer);
}
