package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.PostingValidationBook;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Bookkeeping acceptance policy shared by preflight and durable commit paths. */
public final class PostingAcceptancePolicy {
  private PostingAcceptancePolicy() {}

  /** Returns the first deterministic rejection for the supplied posting attempt, if any. */
  public static Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationBook book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (!book.isInitialized()) {
      return Optional.of(new BookkeepingPostingRejection.BookNotInitialized());
    }
    if (book.findExistingPosting(postingRequest.requestProvenance().idempotencyKey()).isPresent()) {
      return Optional.of(new BookkeepingPostingRejection.DuplicateIdempotencyKey());
    }
    Optional<BookkeepingPostingRejection> accountRejection = accountRejection(postingRequest, book);
    if (accountRejection.isPresent()) {
      return accountRejection;
    }
    return ReversalAcceptancePolicy.rejectionFor(postingRequest, book);
  }

  private static Optional<BookkeepingPostingRejection> accountRejection(
      PostingRequestModel postingRequest, PostingValidationBook book) {
    Set<AccountCode> requestedAccounts = new LinkedHashSet<>();
    for (JournalLine line : postingRequest.journalEntry().lines()) {
      requestedAccounts.add(line.accountCode());
    }
    Map<AccountCode, RegisteredAccount> declaredAccounts = book.findAccounts(requestedAccounts);
    Set<BookkeepingPostingRejection.AccountStateViolation> violations = new LinkedHashSet<>();
    for (AccountCode accountCode : requestedAccounts) {
      RegisteredAccount account = declaredAccounts.get(accountCode);
      if (account == null) {
        violations.add(new BookkeepingPostingRejection.UnknownAccount(accountCode));
        continue;
      }
      if (!account.active()) {
        violations.add(new BookkeepingPostingRejection.InactiveAccount(accountCode));
      }
    }
    if (violations.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new BookkeepingPostingRejection.AccountStateViolations(List.copyOf(violations)));
  }
}
