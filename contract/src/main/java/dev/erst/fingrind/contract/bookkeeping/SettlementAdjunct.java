package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Optional settlement-side adjunct carried by receipt and payment requests. */
public record SettlementAdjunct(AccountCode accountCode, MonetaryAmount amount) {
  /** Validates one settlement adjunct payload. */
  public SettlementAdjunct {
    Objects.requireNonNull(accountCode, "accountCode");
    amount = BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount");
  }
}
