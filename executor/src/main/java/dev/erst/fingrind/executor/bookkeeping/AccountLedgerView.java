package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping running-ledger view for one registered account. */
public record AccountLedgerView(
    RegisteredAccount account,
    EffectiveDateRange effectiveDateRange,
    List<CurrencyBalance> openingBalances,
    List<AccountLedgerEntryView> entries,
    List<CurrencyBalance> closingBalances) {
  public AccountLedgerView {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(openingBalances, "openingBalances");
    Objects.requireNonNull(entries, "entries");
    Objects.requireNonNull(closingBalances, "closingBalances");
    openingBalances = List.copyOf(openingBalances);
    entries = List.copyOf(entries);
    closingBalances = List.copyOf(closingBalances);
  }
}
