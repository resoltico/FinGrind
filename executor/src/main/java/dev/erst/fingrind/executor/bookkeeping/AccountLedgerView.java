package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping running-ledger view for one registered account. */
public record AccountLedgerView(
    RegisteredAccount account,
    EffectiveDateRange effectiveDateRange,
    PostingCoverage postingCoverage,
    int limit,
    Optional<AccountLedgerCursor> cursor,
    Optional<AccountLedgerCursor> nextCursor,
    List<CurrencyBalance> openingBalances,
    List<AccountLedgerEntryView> entries,
    List<CurrencyBalance> closingBalances) {
  public AccountLedgerView {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    if (limit < 1) {
      throw new IllegalArgumentException("Account-ledger limit must be greater than zero.");
    }
    Objects.requireNonNull(cursor, "cursor");
    Objects.requireNonNull(nextCursor, "nextCursor");
    Objects.requireNonNull(openingBalances, "openingBalances");
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(closingBalances, "closingBalances");
    openingBalances = List.copyOf(openingBalances);
    entries = List.copyOf(entries);
    closingBalances = List.copyOf(closingBalances);
  }
}
