package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.executor.bookkeeping.policy.ClosePostingPolicy;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Reserves close-owned equity classifications from ordinary caller-authored postings. */
final class ReservedResultClassificationPolicy {
  private final ClosePostingPolicy closePostingPolicy;

  ReservedResultClassificationPolicy(ClosePostingPolicy closePostingPolicy) {
    this.closePostingPolicy = Objects.requireNonNull(closePostingPolicy, "closePostingPolicy");
  }

  Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, BookIdentity bookIdentity, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(book, "book");
    if (postingRequest.postingKind() != PostingKind.STANDARD) {
      return Optional.empty();
    }
    Set<FinancialPositionLineClassification> reservedClassifications =
        Set.of(
            closePostingPolicy.resultHoldingLineClassification(bookIdentity),
            FinancialPositionLineClassification.RETAINED_ACCUMULATED);
    Set<AccountCode> requestedAccounts = PostingRequestAccounts.requestedAccounts(postingRequest);
    Map<AccountCode, RegisteredAccount> declaredAccounts = book.findAccounts(requestedAccounts);
    BookkeepingPostingRejection reservedRejection = null;
    for (AccountCode accountCode : requestedAccounts) {
      RegisteredAccount account =
          Objects.requireNonNull(declaredAccounts.get(accountCode), "account");
      FinancialPositionLineClassification reservedClassification =
          account.accountTaxonomy().financialPositionLineClassification().orElse(null);
      if (reservedClassification != null
          && reservedClassifications.contains(reservedClassification)) {
        reservedRejection =
            new BookkeepingPostingRejection.ReservedResultClassification(
                accountCode, reservedClassification);
        break;
      }
    }
    return Optional.ofNullable(reservedRejection);
  }
}
