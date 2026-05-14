package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.PostingDraft;
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
    Optional<BookkeepingPostingRejection> postingKindRejection =
        postingKindRejection(postingRequest);
    if (postingKindRejection.isPresent()) {
      return postingKindRejection;
    }
    BookIdentity bookIdentity = initializedBookIdentity(book);
    Optional<BookkeepingPostingRejection> currencyRejection =
        functionalCurrencyRejection(postingRequest, bookIdentity);
    if (currencyRejection.isPresent()) {
      return currencyRejection;
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

  static BookIdentity initializedBookIdentity(PostingValidationStore book) {
    return switch (Objects.requireNonNull(book.inspectBook(), "inspectBook")) {
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Initialized initialized ->
          initialized.bookIdentity();
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Missing _ ->
          throw new IllegalStateException("Initialized posting validation requires one live book.");
      case dev.erst.fingrind.executor.spi.BookLifecycleInspection.Existing _ ->
          throw new IllegalStateException("Initialized posting validation requires one live book.");
    };
  }

  private static Optional<BookkeepingPostingRejection> postingKindRejection(
      PostingRequestModel postingRequest) {
    PostingKind postingKind = postingRequest.postingKind();
    return postingKind.isCallerSelectable() || isInternalSystemPosting(postingRequest)
        ? Optional.empty()
        : Optional.of(new BookkeepingPostingRejection.PostingKindReserved(postingKind));
  }

  static boolean isInternalSystemPosting(PostingRequestModel postingRequest) {
    return switch (postingRequest) {
      case PostingCommand command -> command.sourceChannel() == SourceChannel.SYSTEM;
      case PostingDraft draft -> draft.provenance().sourceChannel() == SourceChannel.SYSTEM;
      default -> false;
    };
  }

  private static Optional<BookkeepingPostingRejection> functionalCurrencyRejection(
      PostingRequestModel postingRequest, BookIdentity bookIdentity) {
    return postingRequest.journalEntry().currencyUnit().equals(bookIdentity.functionalCurrency())
        ? Optional.empty()
        : Optional.of(
            new BookkeepingPostingRejection.BookFunctionalCurrencyMismatch(
                bookIdentity.functionalCurrency(), postingRequest.journalEntry().currencyUnit()));
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
    Optional<BookkeepingPostingRejection> openingBalanceRejection =
        openingBalanceNominalAccountRejection(postingRequest, requestedAccounts, declaredAccounts);
    if (openingBalanceRejection.isPresent()) {
      return openingBalanceRejection;
    }
    return retainedEarningsReservationRejection(
        postingRequest, requestedAccounts, declaredAccounts);
  }

  private static Optional<BookkeepingPostingRejection> openingBalanceNominalAccountRejection(
      PostingRequestModel postingRequest,
      Set<AccountCode> requestedAccounts,
      Map<AccountCode, RegisteredAccount> declaredAccounts) {
    if (!postingRequest.postingKind().isOpeningBalance()) {
      return Optional.empty();
    }
    AccountCode rejectedAccountCode = null;
    AccountType rejectedAccountType = null;
    for (AccountCode accountCode : requestedAccounts) {
      RegisteredAccount account =
          Objects.requireNonNull(declaredAccounts.get(accountCode), "account");
      if (account.accountType() == AccountType.REVENUE
          || account.accountType() == AccountType.EXPENSE) {
        rejectedAccountCode = accountCode;
        rejectedAccountType = account.accountType();
        break;
      }
    }
    return rejectedAccountCode == null
        ? Optional.empty()
        : Optional.of(
            new BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount(
                rejectedAccountCode,
                Objects.requireNonNull(rejectedAccountType, "rejectedAccountType")));
  }

  private static Optional<BookkeepingPostingRejection> retainedEarningsReservationRejection(
      PostingRequestModel postingRequest,
      Set<AccountCode> requestedAccounts,
      Map<AccountCode, RegisteredAccount> declaredAccounts) {
    if (postingRequest.postingKind() != PostingKind.STANDARD) {
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
