package dev.erst.fingrind.contract.bookkeeping;

import java.util.Objects;
import java.util.Optional;

/** Explicit keyset-page boundary for an account-ledger report. */
public record AccountLedgerPagination(
    int limit,
    Optional<AccountLedgerPageCursor> cursor,
    Optional<AccountLedgerPageCursor> nextCursor) {
  /** Validates one account-ledger page boundary. */
  public AccountLedgerPagination {
    if (limit < 1) {
      throw new IllegalArgumentException("Account-ledger limit must be greater than zero.");
    }
    Objects.requireNonNull(cursor, "cursor");
    Objects.requireNonNull(nextCursor, "nextCursor");
  }

  /** Creates an initial page with no continuation boundary. */
  public static AccountLedgerPagination firstPage(int limit) {
    return new AccountLedgerPagination(limit, Optional.empty(), Optional.empty());
  }
}
