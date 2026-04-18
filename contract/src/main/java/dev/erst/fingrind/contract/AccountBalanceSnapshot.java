package dev.erst.fingrind.contract;

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
  public AccountBalanceSnapshot {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    balances = List.copyOf(Objects.requireNonNull(balances, "balances"));
  }
}
