package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Filter request for computing balances on one declared account. */
public record AccountBalanceQuery(
    AccountCode accountCode,
    Optional<LocalDate> effectiveDateFrom,
    Optional<LocalDate> effectiveDateTo) {
  /** Validates one account-balance query. */
  public AccountBalanceQuery {
    Objects.requireNonNull(accountCode, "accountCode");
    effectiveDateFrom = effectiveDateFrom == null ? Optional.empty() : effectiveDateFrom;
    effectiveDateTo = effectiveDateTo == null ? Optional.empty() : effectiveDateTo;
    if (effectiveDateFrom.isPresent()
        && effectiveDateTo.isPresent()
        && effectiveDateFrom.orElseThrow().isAfter(effectiveDateTo.orElseThrow())) {
      throw new IllegalArgumentException(
          "Account balance effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }
}
