package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Canonical doctrine owner for declared account taxonomy validation and polarity. */
public final class AccountTaxonomyDoctrine {
  private AccountTaxonomyDoctrine() {}

  /** Validates one declared account taxonomy for the selected account type. */
  public static void validate(AccountType accountType, AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    Objects.requireNonNull(accountTaxonomy.nodeKind(), "accountTaxonomy.nodeKind");
    if (!ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(accountType)) {
      validateBalanceSheetTaxonomy(accountType, accountTaxonomy);
      return;
    }
    validateNominalTaxonomy(accountType, accountTaxonomy);
  }

  /** Returns the canonical journal side that increases this account. */
  public static NormalBalance normalBalance(
      AccountType accountType, AccountTaxonomy accountTaxonomy) {
    validate(accountType, accountTaxonomy);
    return ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(accountType)
        ? accountTaxonomy.profitAndLossLineClassification().orElseThrow().normalBalance()
        : accountTaxonomy.financialPositionLineClassification().orElseThrow().normalBalance();
  }

  /** Returns whether the declared account participates in cash and cash equivalents. */
  public static boolean cashAndCashEquivalent(
      AccountType accountType, AccountTaxonomy accountTaxonomy) {
    validate(accountType, accountTaxonomy);
    return accountType == AccountType.ASSET
        && accountTaxonomy.cashFlowAssetClassification().orElseThrow().cashAndCashEquivalent();
  }

  private static void validateBalanceSheetTaxonomy(
      AccountType accountType, AccountTaxonomy accountTaxonomy) {
    Optional<FinancialPositionLineClassification> declaredClassification =
        accountTaxonomy.financialPositionLineClassification();
    if (declaredClassification.isEmpty()) {
      throw new IllegalArgumentException(
          "Financial-position classification is required for balance-sheet accounts.");
    }
    if (accountTaxonomy.profitAndLossLineClassification().isPresent()) {
      throw new IllegalArgumentException(
          "Profit-and-loss classification must be absent for balance-sheet accounts.");
    }
    if (accountType == AccountType.ASSET) {
      if (accountTaxonomy.cashFlowAssetClassification().isEmpty()) {
        throw new IllegalArgumentException(
            "cashFlowAssetClassification is required for ASSET accounts. Accepted values: %s."
                .formatted(String.join(", ", CashFlowAssetClassification.wireValues())));
      }
    } else if (accountTaxonomy.cashFlowAssetClassification().isPresent()) {
      throw new IllegalArgumentException(
          "cashFlowAssetClassification must be absent for non-ASSET accounts.");
    }
    if (declaredClassification.orElseThrow().accountType() != accountType) {
      throw new IllegalArgumentException(
          "Financial-position classification must match the declared accountType.");
    }
  }

  private static void validateNominalTaxonomy(
      AccountType accountType, AccountTaxonomy accountTaxonomy) {
    if (accountTaxonomy.profitAndLossLineClassification().isEmpty()) {
      throw new IllegalArgumentException(
          "Profit-and-loss classification is required for nominal accounts.");
    }
    if (accountTaxonomy.financialPositionLineClassification().isPresent()) {
      throw new IllegalArgumentException(
          "Financial-position classification must be absent for nominal accounts.");
    }
    if (accountTaxonomy.cashFlowAssetClassification().isPresent()) {
      throw new IllegalArgumentException(
          "cashFlowAssetClassification must be absent for nominal accounts.");
    }
    if (accountTaxonomy.profitAndLossLineClassification().orElseThrow().accountType()
        != accountType) {
      throw new IllegalArgumentException(
          "Profit-and-loss classification must match the declared accountType.");
    }
  }
}
