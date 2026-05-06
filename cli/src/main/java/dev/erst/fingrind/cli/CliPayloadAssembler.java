package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.core.CurrencyBalance;

/** Shared leaf payload builders reused by multiple CLI JSON mappers. */
final class CliPayloadAssembler {
  private CliPayloadAssembler() {}

  static CliBookQueryJsonModels.BalanceBucketPayload balancePayload(CurrencyBalance balance) {
    return new CliBookQueryJsonModels.BalanceBucketPayload(
        balance.debitTotal().currencyCode().value(),
        balance.debitTotal().amount().toPlainString(),
        balance.creditTotal().amount().toPlainString(),
        balance.netAmount().amount().toPlainString(),
        balance.balanceSide().wireValue());
  }
}
