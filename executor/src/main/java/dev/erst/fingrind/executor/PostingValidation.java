package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PostingRequest;
import dev.erst.fingrind.core.JournalLine;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Shared posting validation rules used by both preflight and durable commit paths. */
public final class PostingValidation {
  private PostingValidation() {}

  /** Returns the first deterministic posting rejection for the supplied attempt, if any. */
  public static Optional<PostingRejection> rejectionFor(
      PostingRequest postingRequest, PostingValidationBook book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (!book.isInitialized()) {
      return Optional.of(new PostingRejection.BookNotInitialized());
    }
    Optional<PostingRejection> accountRejection = accountRejection(postingRequest, book);
    if (accountRejection.isPresent()) {
      return accountRejection;
    }
    if (book.findExistingPosting(postingRequest.requestProvenance().idempotencyKey()).isPresent()) {
      return Optional.of(new PostingRejection.DuplicateIdempotencyKey());
    }
    return ReversalPolicy.rejectionFor(postingRequest, book);
  }

  private static Optional<PostingRejection> accountRejection(
      PostingRequest postingRequest, PostingValidationBook book) {
    Set<PostingRejection.AccountStateViolation> violations = new LinkedHashSet<>();
    for (JournalLine line : postingRequest.journalEntry().lines()) {
      Optional<DeclaredAccount> account = book.findAccount(line.accountCode());
      if (account.isEmpty()) {
        violations.add(new PostingRejection.UnknownAccount(line.accountCode()));
        continue;
      }
      if (!account.orElseThrow().active()) {
        violations.add(new PostingRejection.InactiveAccount(line.accountCode()));
      }
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new PostingRejection.AccountStateViolations(List.copyOf(violations)));
  }
}
