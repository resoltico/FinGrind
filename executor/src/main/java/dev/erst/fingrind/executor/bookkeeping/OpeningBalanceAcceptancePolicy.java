package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validates opening-balance-specific posting invariants. */
final class OpeningBalanceAcceptancePolicy {
  Optional<BookkeepingPostingRejection> windowRejection(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (!postingRequest.postingKind().isOpeningBalance()) {
      return Optional.empty();
    }
    return book.firstCommittedPosting()
        .map(
            firstBlockingPosting ->
                new BookkeepingPostingRejection.OpeningBalanceWindowClosed(
                    firstBlockingPosting.postingKind(),
                    firstBlockingPosting.journalEntry().effectiveDate()));
  }

  Optional<BookkeepingPostingRejection> nominalAccountRejection(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    if (!postingRequest.postingKind().isOpeningBalance()) {
      return Optional.empty();
    }
    Set<AccountCode> requestedAccounts = PostingRequestAccounts.requestedAccounts(postingRequest);
    Map<AccountCode, RegisteredAccount> declaredAccounts = book.findAccounts(requestedAccounts);
    RegisteredAccount nominalAccount = null;
    for (AccountCode accountCode : requestedAccounts) {
      RegisteredAccount account =
          Objects.requireNonNull(declaredAccounts.get(accountCode), "account");
      if (account.accountType() == AccountType.REVENUE
          || account.accountType() == AccountType.EXPENSE) {
        nominalAccount = account;
        break;
      }
    }
    if (nominalAccount == null) {
      return Optional.empty();
    }
    return Optional.of(
        new BookkeepingPostingRejection.OpeningBalanceTouchesNominalAccount(
            nominalAccount.accountCode(), nominalAccount.accountType()));
  }
}
