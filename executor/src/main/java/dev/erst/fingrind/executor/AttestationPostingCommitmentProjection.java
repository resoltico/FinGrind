package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Resolves only authenticated attestation commitments for an exact posting selection. */
public final class AttestationPostingCommitmentProjection {
  private AttestationPostingCommitmentProjection() {}

  /** Resolves commitments and rejects a projection that escapes the requested selection. */
  public static Map<PostingId, AttestationCommit> resolve(
      AttestationPostingCommitmentStore store, Set<PostingId> postingIds) {
    Objects.requireNonNull(store, "store");
    Set<PostingId> requestedPostingIds =
        Set.copyOf(Objects.requireNonNull(postingIds, "postingIds"));
    Map<PostingId, AttestationCommit> commitments =
        Map.copyOf(store.attestationCommitsFor(requestedPostingIds));
    if (!requestedPostingIds.containsAll(commitments.keySet())) {
      throw new IllegalStateException(
          "Posting-attestation projection returned a commitment outside the requested posting selection.");
    }
    return commitments;
  }
}
