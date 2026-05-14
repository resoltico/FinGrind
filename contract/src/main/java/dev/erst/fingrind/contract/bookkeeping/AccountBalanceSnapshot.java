package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Computed per-currency balances for one declared account. */
public record AccountBalanceSnapshot(
    BookIdentity bookIdentity,
    DeclaredAccount account,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo,
    PostingCoverage postingCoverage,
    List<CurrencyBalance> balances) {
  /** Validates one account-balance snapshot. */
  public AccountBalanceSnapshot(
      BookIdentity bookIdentity,
      DeclaredAccount account,
      Optional<LocalDate> effectiveDateFrom,
      Optional<LocalDate> effectiveDateTo,
      PostingCoverage postingCoverage,
      List<CurrencyBalance> balances) {
    this.bookIdentity = Objects.requireNonNull(bookIdentity, "bookIdentity");
    this.account = Objects.requireNonNull(account, "account");
    this.effectiveDateFrom = Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    this.effectiveDateTo = Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    this.postingCoverage = Objects.requireNonNull(postingCoverage, "postingCoverage");
    this.balances = ContractDescriptorValidation.copyList(balances, "balances");
  }
}
