package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Published posting-page enrichment for reversal backlinks. */
final class BookReadPostingBacklinkProjection {
  private BookReadPostingBacklinkProjection() {}

  static dev.erst.fingrind.contract.bookkeeping.PostingPage withReversalBacklinks(
      BookkeepingReadStore bookStore,
      dev.erst.fingrind.contract.bookkeeping.PostingPage page,
      PostingHistoryPage reportedPage) {
    Objects.requireNonNull(bookStore, "bookStore");
    Objects.requireNonNull(page, "page");
    Objects.requireNonNull(reportedPage, "reportedPage");
    Map<PostingId, PostingId> reversedByPostingIds = new ConcurrentHashMap<>();
    reportedPage
        .postings()
        .forEach(
            posting ->
                bookStore
                    .findReversalFor(posting.postingId())
                    .map(CommittedPosting::postingId)
                    .ifPresent(
                        reversalPostingId ->
                            reversedByPostingIds.put(posting.postingId(), reversalPostingId)));
    return new dev.erst.fingrind.contract.bookkeeping.PostingPage(
        page.bookIdentity(),
        page.accountCodeFilter(),
        page.effectiveDateRange(),
        page.postings(),
        page.limit(),
        page.nextCursor(),
        reversedByPostingIds);
  }
}
