package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import java.util.LinkedHashSet;
import java.util.Set;

/** Resolves referenced accounts for typed fixed-asset business events. */
final class PostEntryFixedAssetSemanticContext {
  private PostEntryFixedAssetSemanticContext() {}

  static Set<AccountCode> referencedAccounts(FixedAssetBookkeepingEntryVariants entry) {
    return switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization ->
          accountSet(
              capitalization.assetAccountCode(),
              capitalization.accumulatedDepreciationAccountCode(),
              capitalization.depreciationExpenseAccountCode(),
              capitalization.disposalGainAccountCode(),
              capitalization.disposalLossAccountCode(),
              capitalization.cashAccountCode());
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation ->
          depreciation.resolvedDepreciation() == null
              ? Set.of()
              : accountSet(
                  depreciation.resolvedDepreciation().depreciationExpenseAccountCode(),
                  depreciation.resolvedDepreciation().accumulatedDepreciationAccountCode());
      case FixedAssetBookkeepingEntryVariants.Disposal disposal ->
          disposal.resolvedDisposal() == null
              ? accountSet(disposal.cashAccountCode())
              : accountSet(
                  disposal.cashAccountCode(),
                  disposal.resolvedDisposal().assetAccountCode(),
                  disposal.resolvedDisposal().accumulatedDepreciationAccountCode(),
                  disposal.resolvedDisposal().gainOrLossAccountCode());
    };
  }

  private static Set<AccountCode> accountSet(AccountCode... accountCodes) {
    Set<AccountCode> accounts = new LinkedHashSet<>();
    java.util.Collections.addAll(accounts, accountCodes);
    return accounts;
  }
}
