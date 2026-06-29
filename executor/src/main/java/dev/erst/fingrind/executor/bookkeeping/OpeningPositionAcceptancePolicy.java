package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.AccountCode;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validates OPENING_POSITION admission rules after entry translation. */
final class OpeningPositionAcceptancePolicy {
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
                new BookkeepingPostingRejection.OpeningPositionWindowClosed(
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
      if (!AccountClassificationReachability.openingReachable(account.accountTaxonomy())) {
        nominalAccount = account;
        break;
      }
    }
    if (nominalAccount == null) {
      return Optional.empty();
    }
    return Optional.of(
        new BookkeepingPostingRejection.OpeningPositionTouchesNominalAccount(
            nominalAccount.accountCode(), nominalAccount.accountType()));
  }
}
