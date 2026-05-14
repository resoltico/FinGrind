package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping balance view for one registered account and one date-range filter. */
public record AccountBalanceView(
    RegisteredAccount account,
    EffectiveDateRange effectiveDateRange,
    PostingCoverage postingCoverage,
    List<CurrencyBalance> balances) {
  public AccountBalanceView {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    Objects.requireNonNull(balances, "balances");
    balances = List.copyOf(balances);
  }
}
