package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;

/** Canonical running-balance ledger report for one declared account. */
public record AccountLedgerReport(
    DeclaredAccount account,
    EffectiveDateRange effectiveDateRange,
    List<CurrencyBalance> openingBalances,
    List<AccountLedgerEntry> entries,
    List<CurrencyBalance> closingBalances) {
  /** Validates one account-ledger report. */
  public AccountLedgerReport {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    openingBalances = openingBalances == null ? List.of() : List.copyOf(openingBalances);
    entries = entries == null ? List.of() : List.copyOf(entries);
    closingBalances = closingBalances == null ? List.of() : List.copyOf(closingBalances);
  }
}
