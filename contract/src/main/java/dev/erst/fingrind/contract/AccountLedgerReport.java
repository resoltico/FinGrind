package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical running-balance ledger report for one declared account. */
public record AccountLedgerReport(
    DeclaredAccount account,
    EffectiveDateRange effectiveDateRange,
    List<CurrencyBalance> openingBalances,
    List<AccountLedgerEntry> entries,
    List<CurrencyBalance> closingBalances) {
  /** Validates one account-ledger report. */
  public AccountLedgerReport(
      DeclaredAccount account,
      EffectiveDateRange effectiveDateRange,
      @Nullable List<CurrencyBalance> openingBalances,
      @Nullable List<AccountLedgerEntry> entries,
      @Nullable List<CurrencyBalance> closingBalances) {
    this.account = Objects.requireNonNull(account, "account");
    this.effectiveDateRange = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    this.openingBalances = openingBalances == null ? List.of() : List.copyOf(openingBalances);
    this.entries = entries == null ? List.of() : List.copyOf(entries);
    this.closingBalances = closingBalances == null ? List.of() : List.copyOf(closingBalances);
  }
}
