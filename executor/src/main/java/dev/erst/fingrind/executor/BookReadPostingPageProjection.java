package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Enriches one published posting page with read-side links owned by its backing book. */
final class BookReadPostingPageProjection {
  private BookReadPostingPageProjection() {}

  static PostingPage enrich(
      BookkeepingReadStore bookStore,
      AttestationPostingCommitmentStore attestationCommitmentStore,
      PostingPage page,
      PostingHistoryPage reportedPage) {
    Objects.requireNonNull(bookStore, "bookStore");
    Objects.requireNonNull(attestationCommitmentStore, "attestationCommitmentStore");
    Objects.requireNonNull(page, "page");
    Objects.requireNonNull(reportedPage, "reportedPage");
    Set<PostingId> postingIds = new LinkedHashSet<>();
    Map<PostingId, PostingId> reversedByPostingIds = new ConcurrentHashMap<>();
    for (CommittedPosting posting : reportedPage.postings()) {
      PostingId postingId = posting.postingId();
      postingIds.add(postingId);
      bookStore
          .findReversalFor(postingId)
          .map(CommittedPosting::postingId)
          .ifPresent(reversalPostingId -> reversedByPostingIds.put(postingId, reversalPostingId));
    }
    Map<PostingId, AttestationCommit> attestationCommitsByPostingId =
        AttestationPostingCommitmentProjection.resolve(attestationCommitmentStore, postingIds);
    return new PostingPage(
        page.bookIdentity(),
        page.accountCodeFilter(),
        page.effectiveDateRange(),
        page.postings(),
        page.limit(),
        page.nextCursor(),
        reversedByPostingIds,
        attestationCommitsByPostingId);
  }
}
