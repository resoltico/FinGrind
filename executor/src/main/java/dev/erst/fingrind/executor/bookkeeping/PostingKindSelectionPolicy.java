package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.PostingKind;
import java.util.Optional;

/** Validates which posting kinds callers may select directly. */
final class PostingKindSelectionPolicy {
  Optional<BookkeepingPostingRejection> rejectionFor(PostingRequestModel postingRequest) {
    PostingKind postingKind = postingRequest.postingKind();
    return postingKind.isCallerSelectable()
            || PostingAcceptancePolicy.isInternalSystemPosting(postingRequest)
        ? Optional.empty()
        : Optional.of(new BookkeepingPostingRejection.PostingKindReserved(postingKind));
  }
}
