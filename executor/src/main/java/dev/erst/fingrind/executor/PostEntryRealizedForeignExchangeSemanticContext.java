package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves referenced accounts for typed realized-foreign-exchange business events. */
final class PostEntryRealizedForeignExchangeSemanticContext {
  private PostEntryRealizedForeignExchangeSemanticContext() {}

  static Set<AccountCode> referencedAccounts(
      RealizedForeignExchangeBookkeepingEntryVariants entry) {
    return switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable ->
          accountSet(
              receivable.receivableAccountCode(),
              receivable.revenueAccountCode(),
              receivable.realizedGainAccountCode(),
              receivable.realizedLossAccountCode());
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement ->
          settlement.resolvedSettlement() == null
              ? accountSet(settlement.cashAccountCode())
              : accountSet(
                  settlement.cashAccountCode(),
                  settlement.resolvedSettlement().receivableAccountCode(),
                  settlement.resolvedSettlement().gainOrLossAccountCode());
    };
  }

  private static Set<AccountCode> accountSet(AccountCode... accountCodes) {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    java.util.Collections.addAll(accounts, accountCodes);
    return accounts;
  }
}
