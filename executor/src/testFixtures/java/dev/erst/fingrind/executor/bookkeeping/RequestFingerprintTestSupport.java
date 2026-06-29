package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.executor.spi.PostingDraft;
import java.util.Objects;

/** Test-only access to the canonical request-fingerprint owner. */
public final class RequestFingerprintTestSupport {
  private RequestFingerprintTestSupport() {}

  /** Returns one canonical semantic fingerprint for the supplied posting request model. */
  public static RequestFingerprint fingerprint(PostingRequestModel postingRequest) {
    return RequestFingerprintOwner.fingerprint(postingRequest);
  }

  /** Builds one posting draft with the canonical fingerprint that the kernel now derives. */
  public static PostingDraft fingerprintedDraft(
      dev.erst.fingrind.core.JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      AccountingEvidence evidence,
      CommittedProvenance provenance) {
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(evidence, "evidence");
    Objects.requireNonNull(provenance, "provenance");
    PostingDraft placeholderDraft =
        new PostingDraft(
            journalEntry,
            postingLineage,
            postingKind,
            postingOriginKind,
            evidence,
            placeholderFingerprint(),
            provenance);
    return new PostingDraft(
        journalEntry,
        postingLineage,
        postingKind,
        postingOriginKind,
        evidence,
        fingerprint(placeholderDraft),
        provenance);
  }

  private static RequestFingerprint placeholderFingerprint() {
    return new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64));
  }
}
