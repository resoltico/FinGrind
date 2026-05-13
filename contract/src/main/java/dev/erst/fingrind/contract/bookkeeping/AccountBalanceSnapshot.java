package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.CurrencyBalance;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Computed per-currency balances for one declared account. */
public record AccountBalanceSnapshot(
    DeclaredAccount account,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo,
    List<CurrencyBalance> balances) {
  /** Validates one account-balance snapshot. */
  public AccountBalanceSnapshot(
      DeclaredAccount account,
      Optional<LocalDate> effectiveDateFrom,
      Optional<LocalDate> effectiveDateTo,
      List<CurrencyBalance> balances) {
    this.account = Objects.requireNonNull(account, "account");
    this.effectiveDateFrom = Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    this.effectiveDateTo = Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    this.balances = ContractDescriptorValidation.copyList(balances, "balances");
  }
}
