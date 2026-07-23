package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.PostingId;
import java.util.Map;
import java.util.Set;

/** Reads authenticated operation commitments for committed postings in one initialized book. */
@FunctionalInterface
public interface AttestationPostingCommitmentStore {
  /**
   * Returns only commitments authenticated by the complete immutable operation chain.
   *
   * <p>Absent keys have no authenticated operation reference. Implementations must reject invalid
   * evidence rather than returning a partial or unverified projection.
   */
  Map<PostingId, AttestationCommit> attestationCommitsFor(Set<PostingId> postingIds);
}
