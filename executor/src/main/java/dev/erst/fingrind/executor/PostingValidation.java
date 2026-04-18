package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.PostingRequest;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    if (book.findExistingPosting(postingRequest.requestProvenance().idempotencyKey()).isPresent()) {
      return Optional.of(new PostingRejection.DuplicateIdempotencyKey());
    }
    Optional<PostingRejection> accountRejection = accountRejection(postingRequest, book);
    if (accountRejection.isPresent()) {
      return accountRejection;
    }
    return ReversalPolicy.rejectionFor(postingRequest, book);
  }

  private static Optional<PostingRejection> accountRejection(
      PostingRequest postingRequest, PostingValidationBook book) {
    Set<AccountCode> requestedAccounts = new LinkedHashSet<>();
    for (JournalLine line : postingRequest.journalEntry().lines()) {
      requestedAccounts.add(line.accountCode());
    }
    Map<AccountCode, DeclaredAccount> declaredAccounts = book.findAccounts(requestedAccounts);
    Set<PostingRejection.AccountStateViolation> violations = new LinkedHashSet<>();
    for (AccountCode accountCode : requestedAccounts) {
      DeclaredAccount account = declaredAccounts.get(accountCode);
      if (account == null) {
        violations.add(new PostingRejection.UnknownAccount(accountCode));
        continue;
      }
      if (!account.active()) {
        violations.add(new PostingRejection.InactiveAccount(accountCode));
      }
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new PostingRejection.AccountStateViolations(List.copyOf(violations)));
  }
}
