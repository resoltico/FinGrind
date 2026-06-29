package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Validates that posting effective dates remain outside closed horizons. */
final class PostingSweptInterimResultPolicy {
  Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (postingRequest.postingKind() == dev.erst.fingrind.core.PostingKind.FISCAL_YEAR_CLOSE
        && PostingAcceptancePolicy.isInternalSystemPosting(postingRequest)) {
      return Optional.empty();
    }
    LocalDate effectiveDate = postingRequest.journalEntry().effectiveDate();
    return book.transferredThroughEffectiveDate()
        .filter(closedThrough -> !effectiveDate.isAfter(closedThrough))
        .<BookkeepingPostingRejection>map(
            closedThrough ->
                new BookkeepingPostingRejection.SweptInterimResultViolation(
                    closedThrough, effectiveDate));
  }
}
