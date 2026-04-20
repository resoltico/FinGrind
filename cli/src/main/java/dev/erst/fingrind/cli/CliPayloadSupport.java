package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.CurrencyBalance;

/** Shared leaf payload builders reused by multiple CLI JSON mappers. */
final class CliPayloadSupport {
  private CliPayloadSupport() {}

  static CliResponseJsonModels.BalanceBucketPayload balancePayload(CurrencyBalance balance) {
    return new CliResponseJsonModels.BalanceBucketPayload(
        balance.debitTotal().currencyCode().value(),
        balance.debitTotal().amount().toPlainString(),
        balance.creditTotal().amount().toPlainString(),
        balance.netAmount().amount().toPlainString(),
        balance.balanceSide().wireValue());
  }
}
