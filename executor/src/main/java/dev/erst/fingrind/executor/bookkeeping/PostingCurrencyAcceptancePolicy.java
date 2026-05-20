package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import java.util.Objects;
import java.util.Optional;

/** Validates functional-currency alignment for posting requests. */
final class PostingCurrencyAcceptancePolicy {
  Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, BookIdentity bookIdentity) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return postingRequest.journalEntry().currencyUnit().equals(bookIdentity.functionalCurrency())
        ? Optional.empty()
        : Optional.of(
            new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
                bookIdentity.functionalCurrency(), postingRequest.journalEntry().currencyUnit()));
  }
}
