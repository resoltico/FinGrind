package dev.erst.fingrind.cli;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Objects;
import java.util.Optional;

/** Synthetic account-type and taxonomy doctrine shared by Jazzer account fixtures. */
final class CliFuzzSyntheticAccountDoctrine {
  private CliFuzzSyntheticAccountDoctrine() {}

  static AccountType accountType(AccountCode accountCode) {
    String normalized = Objects.requireNonNull(accountCode, "accountCode").value().strip();
    if (Character.isDigit(normalized.charAt(0))) {
      return digitBackedAccountType(normalized.charAt(0), normalized);
    }
    return hashedAccountType(normalized);
  }

  static AccountTaxonomy accountTaxonomy(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_ASSET);
      case LIABILITY ->
          financialPositionTaxonomy(FinancialPositionLineClassification.CURRENT_LIABILITY);
      case EQUITY -> financialPositionTaxonomy(FinancialPositionLineClassification.OTHER_EQUITY);
      case REVENUE -> profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE);
      case EXPENSE -> profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE);
    };
  }

  static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification classification) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(Objects.requireNonNull(classification, "classification")),
        Optional.empty(),
        classification.accountType() == AccountType.ASSET
            ? Optional.of(CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)
            : Optional.empty());
  }

  static AccountTaxonomy profitAndLossTaxonomy(ProfitAndLossLineClassification classification) {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(Objects.requireNonNull(classification, "classification")));
  }

  private static AccountType digitBackedAccountType(
      char leadingDigit, String normalizedAccountCode) {
    return switch (leadingDigit) {
      case '1' -> AccountType.ASSET;
      case '2' -> AccountType.LIABILITY;
      case '3' -> AccountType.EQUITY;
      case '4' -> AccountType.REVENUE;
      case '5', '6', '7', '8', '9' -> AccountType.EXPENSE;
      default -> hashedAccountType(normalizedAccountCode);
    };
  }

  private static AccountType hashedAccountType(String normalizedAccountCode) {
    return switch (Math.floorMod(normalizedAccountCode.hashCode(), 5)) {
      case 0 -> AccountType.ASSET;
      case 1 -> AccountType.LIABILITY;
      case 2 -> AccountType.EQUITY;
      case 3 -> AccountType.REVENUE;
      default -> AccountType.EXPENSE;
    };
  }
}
