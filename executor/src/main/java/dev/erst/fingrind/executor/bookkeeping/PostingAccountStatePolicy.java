package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountStructureDoctrine;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Validates that every requested posting account is known and active. */
final class PostingAccountStatePolicy {
  Optional<BookkeepingPostingRejection> rejectionFor(
      PostingRequestModel postingRequest, PostingValidationStore book) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Objects.requireNonNull(book, "book");
    Set<AccountCode> requestedAccounts = PostingRequestAccounts.requestedAccounts(postingRequest);
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
        continue;
      }
      if (!AccountStructureDoctrine.allowsPosting(account.accountTaxonomy())) {
        violations.add(
            new BookkeepingPostingRejection.NonPostableAccount(
                accountCode, account.accountTaxonomy().nodeKind()));
      }
    }
    return violations.isEmpty()
        ? Optional.empty()
        : Optional.of(
            new BookkeepingPostingRejection.AccountStateViolations(List.copyOf(violations)));
  }
}
