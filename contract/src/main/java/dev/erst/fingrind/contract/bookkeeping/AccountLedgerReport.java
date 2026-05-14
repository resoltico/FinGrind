package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.List;
import java.util.Objects;

/** Canonical running-balance ledger report for one declared account. */
public record AccountLedgerReport(
    BookIdentity bookIdentity,
    DeclaredAccount account,
    EffectiveDateRange effectiveDateRange,
    PostingCoverage postingCoverage,
    List<CurrencyBalance> openingBalances,
    List<AccountLedgerEntry> entries,
    List<CurrencyBalance> closingBalances) {
  /** Validates one account-ledger report. */
  public AccountLedgerReport(
      BookIdentity bookIdentity,
      DeclaredAccount account,
      EffectiveDateRange effectiveDateRange,
      PostingCoverage postingCoverage,
      List<CurrencyBalance> openingBalances,
      List<AccountLedgerEntry> entries,
      List<CurrencyBalance> closingBalances) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    this.account = Objects.requireNonNull(account, "account");
    this.effectiveDateRange = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    this.postingCoverage = Objects.requireNonNull(postingCoverage, "postingCoverage");
    this.openingBalances =
        ContractDescriptorValidation.copyList(openingBalances, "openingBalances");
    this.entries = ContractDescriptorValidation.copyList(entries, "entries");
    this.closingBalances =
        ContractDescriptorValidation.copyList(closingBalances, "closingBalances");
  }
}
