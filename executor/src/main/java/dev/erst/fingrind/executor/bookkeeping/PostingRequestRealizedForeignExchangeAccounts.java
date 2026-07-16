package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;

/** Adds typed realized-foreign-exchange accounts to a posting request's canonical account set. */
final class PostingRequestRealizedForeignExchangeAccounts {
  private PostingRequestRealizedForeignExchangeAccounts() {}

  static void add(
      Set<AccountCode> accounts, RealizedForeignExchangeBookkeepingEntryVariants entry) {
    switch (entry) {
      case RealizedForeignExchangeBookkeepingEntryVariants.ForeignCurrencyReceivable receivable -> {
        accounts.add(receivable.receivableAccountCode());
        accounts.add(receivable.revenueAccountCode());
        accounts.add(receivable.realizedGainAccountCode());
        accounts.add(receivable.realizedLossAccountCode());
      }
      case RealizedForeignExchangeBookkeepingEntryVariants.Settlement settlement -> {
        accounts.add(settlement.cashAccountCode());
        if (settlement.resolvedSettlement() != null) {
          accounts.add(settlement.resolvedSettlement().receivableAccountCode());
          accounts.add(settlement.resolvedSettlement().gainOrLossAccountCode());
        }
      }
    }
  }
}
