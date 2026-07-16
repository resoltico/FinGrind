package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.PostEntryAccountDistinctness.distinct;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.cash;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.expense;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.nonCurrentAsset;
import static dev.erst.fingrind.executor.PostEntryOperatingAccountExpectations.revenue;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetBookkeepingEntryVariants;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Role-account admission for the fixed-asset typed write vocabulary. */
final class PostEntryFixedAssetRoleAccountSemantics {
  private PostEntryFixedAssetRoleAccountSemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      FixedAssetBookkeepingEntryVariants entry,
      String selectorField,
      String selectorValue) {
    switch (entry) {
      case FixedAssetBookkeepingEntryVariants.Capitalization capitalization ->
          validateCapitalization(
              violations, accounts, capitalization, selectorField, selectorValue);
      case FixedAssetBookkeepingEntryVariants.Depreciation depreciation -> {
        if (depreciation.resolvedDepreciation() != null) {
          PostEntryRoleAccountSemantics.validatePair(
              violations,
              accounts,
              selectorField,
              selectorValue,
              expense(
                  depreciation.resolvedDepreciation().depreciationExpenseAccountCode(),
                  "depreciationExpenseAccountCode"),
              nonCurrentAsset(
                  depreciation.resolvedDepreciation().accumulatedDepreciationAccountCode(),
                  "accumulatedDepreciationAccountCode"));
        }
      }
      case FixedAssetBookkeepingEntryVariants.Disposal disposal -> {
        if (disposal.resolvedDisposal() == null) {
          return;
        }
        List<PostEntryAccountExpectation> expectations =
            List.of(
                cash(disposal.cashAccountCode(), "cashAccountCode"),
                nonCurrentAsset(disposal.resolvedDisposal().assetAccountCode(), "assetAccountCode"),
                nonCurrentAsset(
                    disposal.resolvedDisposal().accumulatedDepreciationAccountCode(),
                    "accumulatedDepreciationAccountCode"),
                disposal.resolvedDisposal().gain()
                    ? revenue(
                        disposal.resolvedDisposal().gainOrLossAccountCode(),
                        "disposalGainAccountCode")
                    : expense(
                        disposal.resolvedDisposal().gainOrLossAccountCode(),
                        "disposalLossAccountCode"));
        validateAllDistinct(violations, accounts, selectorField, selectorValue, expectations);
      }
    }
  }

  private static void validateCapitalization(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      FixedAssetBookkeepingEntryVariants.Capitalization capitalization,
      String selectorField,
      String selectorValue) {
    List<PostEntryAccountExpectation> expectations =
        List.of(
            nonCurrentAsset(capitalization.assetAccountCode(), "assetAccountCode"),
            nonCurrentAsset(
                capitalization.accumulatedDepreciationAccountCode(),
                "accumulatedDepreciationAccountCode"),
            expense(
                capitalization.depreciationExpenseAccountCode(), "depreciationExpenseAccountCode"),
            revenue(capitalization.disposalGainAccountCode(), "disposalGainAccountCode"),
            expense(capitalization.disposalLossAccountCode(), "disposalLossAccountCode"),
            cash(capitalization.cashAccountCode(), "cashAccountCode"));
    validateAllDistinct(violations, accounts, selectorField, selectorValue, expectations);
  }

  private static void validateAllDistinct(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      List<PostEntryAccountExpectation> expectations) {
    List<PostEntryDistinctAccountPair> pairs = new ArrayList<>();
    for (int first = 0; first < expectations.size(); first++) {
      for (int second = first + 1; second < expectations.size(); second++) {
        pairs.add(distinct(expectations.get(first), expectations.get(second)));
      }
    }
    PostEntryRoleAccountValidationSupport.validate(
        violations,
        accounts,
        selectorField,
        selectorValue,
        pairs,
        expectations.toArray(PostEntryAccountExpectation[]::new));
  }
}
