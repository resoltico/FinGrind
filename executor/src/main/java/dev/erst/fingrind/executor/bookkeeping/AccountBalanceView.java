package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping balance view for one registered account and one date-range filter. */
public record AccountBalanceView(
    RegisteredAccount account,
    EffectiveDateRange effectiveDateRange,
    List<CurrencyBalance> balances) {
  public AccountBalanceView {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(balances, "balances");
    balances = List.copyOf(balances);
  }
}
