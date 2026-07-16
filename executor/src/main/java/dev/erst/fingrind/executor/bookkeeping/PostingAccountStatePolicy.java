package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountStructureDoctrine;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validates that posting accounts are known, postable, and active for ordinary authored use. */
public final class PostingAccountStatePolicy {
  /** Returns the first declared-account rejection for one caller-authored posting request. */
  public Optional<BookkeepingPostingRejection> declaredAccountRejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    Set<AccountCode> requestedAccounts = PostingRequestAccounts.requestedAccounts(postingRequest);
    Map<AccountCode, RegisteredAccount> declaredAccounts = book.findAccounts(requestedAccounts);
    return declaredAccountRejectionFor(requestedAccounts, declaredAccounts, false);
  }

  Optional<BookkeepingPostingRejection> rejectionFor(
      AcceptedPosting postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    Set<AccountCode> requestedAccounts = PostingRequestAccounts.requestedAccounts(postingRequest);
    Map<AccountCode, RegisteredAccount> declaredAccounts = book.findAccounts(requestedAccounts);
    Optional<BookkeepingPostingRejection> declaredAccountRejection =
        declaredAccountRejectionFor(
            requestedAccounts, declaredAccounts, postingRequest.postingLineage().isReversal());
    if (declaredAccountRejection.isPresent()) {
      return declaredAccountRejection;
    }
    return Optional.empty();
  }

  private static Optional<BookkeepingPostingRejection> declaredAccountRejectionFor(
      Set<AccountCode> requestedAccounts,
      Map<AccountCode, RegisteredAccount> declaredAccounts,
      boolean historicalReversal) {
    Set<BookkeepingPostingRejection.AccountStateViolation> violations = new LinkedHashSet<>();
    for (AccountCode accountCode : requestedAccounts) {
      RegisteredAccount account = declaredAccounts.get(accountCode);
      if (account == null) {
        violations.add(new BookkeepingPostingRejection.UnknownAccount(accountCode));
        continue;
      }
      if (!account.active() && !historicalReversal) {
        violations.add(new BookkeepingPostingRejection.InactiveAccount(accountCode));
        continue;
      }
      if (!AccountStructureDoctrine.allowsPosting(account.accountTaxonomy())) {
        violations.add(
            new BookkeepingPostingRejection.NonPostableAccount(
                accountCode, account.accountTaxonomy().nodeKind()));
      }
    }
    if (!violations.isEmpty()) {
      return Optional.of(
          new BookkeepingPostingRejection.AccountStateViolations(List.copyOf(violations)));
    }
    return Optional.empty();
  }
}
