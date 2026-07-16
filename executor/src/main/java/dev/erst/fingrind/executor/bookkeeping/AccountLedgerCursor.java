package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.PostingId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Local keyset cursor for ascending account-ledger pagination. */
public record AccountLedgerCursor(
    LocalDate effectiveDate, Instant recordedAt, PostingId postingId) {
  public AccountLedgerCursor {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(recordedAt, "recordedAt");
    Objects.requireNonNull(postingId, "postingId");
  }

  /** Builds one account-ledger cursor from the final posting in a page. */
  public static AccountLedgerCursor fromPosting(CommittedPosting posting) {
    Objects.requireNonNull(posting, "posting");
    return new AccountLedgerCursor(
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.postingId());
  }
}
