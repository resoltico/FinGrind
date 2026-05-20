package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Extracts canonical requested-account sets from posting requests. */
final class PostingRequestAccounts {
  private PostingRequestAccounts() {}

  static Set<AccountCode> requestedAccounts(PostingRequestModel postingRequest) {
    Objects.requireNonNull(postingRequest, "postingRequest");
    Set<AccountCode> requestedAccounts = new LinkedHashSet<>();
    for (JournalLine line : postingRequest.journalEntry().lines()) {
      requestedAccounts.add(line.accountCode());
    }
    return requestedAccounts;
  }
}
