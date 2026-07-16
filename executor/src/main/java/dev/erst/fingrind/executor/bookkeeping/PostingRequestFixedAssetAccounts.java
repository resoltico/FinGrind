package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.Set;

/** Adds typed fixed-asset event accounts to a posting request's canonical account set. */
final class PostingRequestFixedAssetAccounts {
  private PostingRequestFixedAssetAccounts() {}

  static void add(Set<AccountCode> accounts, FixedAssetBookkeepingEntryVariants entry) {
    switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization -> {
        accounts.add(capitalization.assetAccountCode());
        accounts.add(capitalization.accumulatedDepreciationAccountCode());
        accounts.add(capitalization.depreciationExpenseAccountCode());
        accounts.add(capitalization.disposalGainAccountCode());
        accounts.add(capitalization.disposalLossAccountCode());
        accounts.add(capitalization.cashAccountCode());
      }
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation -> {
        if (depreciation.resolvedDepreciation() != null) {
          accounts.add(depreciation.resolvedDepreciation().depreciationExpenseAccountCode());
          accounts.add(depreciation.resolvedDepreciation().accumulatedDepreciationAccountCode());
        }
      }
      case FixedAssetBookkeepingEntryVariants.Disposal disposal -> {
        accounts.add(disposal.cashAccountCode());
        if (disposal.resolvedDisposal() != null) {
          accounts.add(disposal.resolvedDisposal().assetAccountCode());
          accounts.add(disposal.resolvedDisposal().accumulatedDepreciationAccountCode());
          accounts.add(disposal.resolvedDisposal().gainOrLossAccountCode());
        }
      }
    }
  }
}
