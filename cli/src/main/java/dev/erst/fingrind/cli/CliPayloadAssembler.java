package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.CurrencyBalance;

/** Shared leaf payload builders reused by multiple CLI JSON mappers. */
final class CliPayloadAssembler {
  private CliPayloadAssembler() {}

  static CliBookQueryJsonModels.BalanceBucketPayload balancePayload(CurrencyBalance balance) {
    return new CliBookQueryJsonModels.BalanceBucketPayload(
        MonetaryAmount.of(balance.debitTotal()),
        MonetaryAmount.of(balance.creditTotal()),
        MonetaryAmount.of(balance.netAmount()),
        balance.balanceSide().wireValue());
  }
}
