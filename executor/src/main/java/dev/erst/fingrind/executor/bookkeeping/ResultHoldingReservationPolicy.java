package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.executor.bookkeeping.policy.ResultTransferPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Reserves policy-owned result-holding accounts from ordinary caller-authored postings. */
final class ResultHoldingReservationPolicy {
  private final ResultTransferPolicy resultTransferPolicy;

  ResultHoldingReservationPolicy(ResultTransferPolicy resultTransferPolicy) {
    this.resultTransferPolicy =
        Objects.requireNonNull(resultTransferPolicy, "resultTransferPolicy");
  }

  Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, BookIdentity bookIdentity, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(book, "book");
    if (postingRequest.postingKind() != PostingKind.STANDARD) {
      return Optional.empty();
    }
    var reservedClassification = resultTransferPolicy.resultHoldingLineClassification(bookIdentity);
    Set<AccountCode> requestedAccounts = PostingRequestAccounts.requestedAccounts(postingRequest);
    Map<AccountCode, RegisteredAccount> declaredAccounts = book.findAccounts(requestedAccounts);
    AccountCode reservedAccountCode = null;
    for (AccountCode accountCode : requestedAccounts) {
      RegisteredAccount account =
          Objects.requireNonNull(declaredAccounts.get(accountCode), "account");
      if (account
          .accountTaxonomy()
          .financialPositionLineClassification()
          .filter(classification -> classification == reservedClassification)
          .isPresent()) {
        reservedAccountCode = accountCode;
        break;
      }
    }
    return reservedAccountCode == null
        ? Optional.empty()
        : Optional.of(
            new BookkeepingPostingRejection.ResultHoldingAccountReserved(reservedAccountCode));
  }
}
