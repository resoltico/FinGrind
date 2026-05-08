package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
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
  public AccountLedgerReport(
      DeclaredAccount account,
      EffectiveDateRange effectiveDateRange,
      List<CurrencyBalance> openingBalances,
      List<AccountLedgerEntry> entries,
      List<CurrencyBalance> closingBalances) {
    this.account = Objects.requireNonNull(account, "account");
    this.effectiveDateRange = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    this.openingBalances =
        ContractDescriptorValidation.copyList(openingBalances, "openingBalances");
    this.entries = ContractDescriptorValidation.copyList(entries, "entries");
    this.closingBalances =
        ContractDescriptorValidation.copyList(closingBalances, "closingBalances");
  }
}
