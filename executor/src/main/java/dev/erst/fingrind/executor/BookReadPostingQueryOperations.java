package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPagePublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Objects;

/** Application ownership for posting-inspection and history-query translation. */
final class BookReadPostingQueryOperations {
  private final BookkeepingReadStore bookStore;
  private final BookkeepingReadService bookkeepingReadService;

  BookReadPostingQueryOperations(
      BookkeepingReadStore bookStore, BookkeepingReadService bookkeepingReadService) {
    this.bookStore = Objects.requireNonNull(bookStore, "bookStore");
    this.bookkeepingReadService =
        Objects.requireNonNull(bookkeepingReadService, "bookkeepingReadService");
  }

  GetPostingResult getPosting(PostingId postingId) {
    BookkeepingReadOutcome<CommittedPosting> outcome = bookkeepingReadService.getPosting(postingId);
    if (outcome instanceof BookkeepingReadOutcome.Reported<CommittedPosting> reported) {
      return new GetPostingResult.Found(
          bookkeepingReadService.requireInitializedBookIdentity(),
          BookkeepingPublishedLanguageTranslator.toPublished(reported.value()),
          bookStore.findReversalFor(postingId).map(CommittedPosting::postingId));
    }
    BookkeepingReadOutcome.Rejected<CommittedPosting> rejected =
        (BookkeepingReadOutcome.Rejected<CommittedPosting>) outcome;
    return new GetPostingResult.Rejected(
        BookReadOutcomeMapper.toPublishedRejection(rejected.rejection()));
  }

  ListPostingsResult listPostings(ListPostingsQuery query) {
    PostingHistoryQuery publishedQuery = BookReadQueryTranslator.fromPublished(query);
    return switch (bookkeepingReadService.listPostings(publishedQuery)) {
      case BookkeepingReadOutcome.Reported<PostingHistoryPage> reported ->
          new ListPostingsResult.Listed(
              BookReadPostingBacklinkProjection.withReversalBacklinks(
                  bookStore,
                  BookkeepingReadPagePublishedLanguageTranslator.toPublished(
                      bookkeepingReadService.requireInitializedBookIdentity(),
                      publishedQuery,
                      reported.value()),
                  reported.value()));
      case BookkeepingReadOutcome.Rejected<PostingHistoryPage> rejected ->
          new ListPostingsResult.Rejected(
              BookReadOutcomeMapper.toPublishedRejection(rejected.rejection()));
    };
  }
}
