package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Public JSON payload for one opening-position balance line. */
public record CliOpeningBalancePayload(
    String accountCode, String side, MonetaryAmount amount, @Nullable String quantity) {
  public CliOpeningBalancePayload {
    accountCode = requireText(accountCode, "accountCode");
    side = requireText(side, "side");
    Objects.requireNonNull(amount, "amount");
  }
}
