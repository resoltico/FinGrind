package dev.erst.fingrind.testsupport;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.RequestFingerprintTestSupport;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;

/** Shared test-only helpers for current posting draft and commit-result shapes. */
public final class PostingTestFixtures {
  private PostingTestFixtures() {}

  /** Builds one posting draft using the current request-fingerprint carrier shape. */
  public static PostingDraft draft(
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      AccountingEvidence evidence,
      CommittedProvenance provenance) {
    return RequestFingerprintTestSupport.fingerprintedDraft(
        journalEntry, postingLineage, postingKind, postingOriginKind, evidence, provenance);
  }

  /** Wraps one fresh committed posting in the current commit-result success shape. */
  public static PostingCommitResult.Committed committed(CommittedPosting postingFact) {
    return new PostingCommitResult.Committed(postingFact, false);
  }
}
