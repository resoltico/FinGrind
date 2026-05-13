package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import java.time.LocalDate;
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
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (!book.inspectBook().allowsInitializedWorkflow()) {
      return Optional.of(new BookkeepingPostingRejection.BookNotInitialized());
    }
    if (book.findExistingPosting(postingRequest.requestProvenance().idempotencyKey()).isPresent()) {
      return Optional.of(new BookkeepingPostingRejection.DuplicateIdempotencyKey());
    }
    Optional<BookkeepingPostingRejection> closedPeriodRejection =
        closedPeriodRejection(postingRequest, book);
    if (closedPeriodRejection.isPresent()) {
      return closedPeriodRejection;
    }
    Optional<BookkeepingPostingRejection> accountRejection = accountRejection(postingRequest, book);
    if (accountRejection.isPresent()) {
      return accountRejection;
    }
    return ReversalAcceptancePolicy.rejectionFor(postingRequest, book);
  }

  private static Optional<BookkeepingPostingRejection> closedPeriodRejection(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    LocalDate effectiveDate = postingRequest.journalEntry().effectiveDate();
    return book.closedThroughEffectiveDate()
        .filter(closedThrough -> !effectiveDate.isAfter(closedThrough))
        .<BookkeepingPostingRejection>map(
            closedThrough ->
                new BookkeepingPostingRejection.ClosedPeriodViolation(
                    closedThrough, effectiveDate));
  }

  private static Optional<BookkeepingPostingRejection> accountRejection(
      PostingRequestModel postingRequest, PostingValidationStore book) {
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
    if (!violations.isEmpty()) {
      return Optional.of(
          new BookkeepingPostingRejection.AccountStateViolations(List.copyOf(violations)));
    }
    return retainedEarningsReservationRejection(
        postingRequest, requestedAccounts, declaredAccounts);
  }

  private static Optional<BookkeepingPostingRejection> retainedEarningsReservationRejection(
      PostingRequestModel postingRequest,
      Set<AccountCode> requestedAccounts,
      Map<AccountCode, RegisteredAccount> declaredAccounts) {
    if (postingRequest.postingKind() == PostingKind.PERIOD_CLOSE) {
      return Optional.empty();
    }
    AccountCode reservedAccountCode = null;
    for (AccountCode accountCode : requestedAccounts) {
      RegisteredAccount account =
          Objects.requireNonNull(declaredAccounts.get(accountCode), "account");
      if (account.accountRole() == AccountRole.RETAINED_EARNINGS) {
        reservedAccountCode = accountCode;
        break;
      }
    }
    return reservedAccountCode == null
        ? Optional.empty()
        : Optional.of(
            new BookkeepingPostingRejection.RetainedEarningsAccountReserved(reservedAccountCode));
  }
}
