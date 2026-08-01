package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Canonical doctrine owner for account-node structure and hierarchy compatibility. */
public final class AccountStructureDoctrine {
  private AccountStructureDoctrine() {}

  /** Returns whether this taxonomy may accept direct postings. */
  public static boolean allowsPosting(AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    return accountTaxonomy.nodeKind().allowsPosting();
  }

  /** Returns whether this taxonomy may own child accounts. */
  public static boolean allowsChildren(AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    return accountTaxonomy.nodeKind().allowsChildren();
  }

  /** Returns whether one parent-child hierarchy edge preserves the declared reporting meaning. */
  public static boolean parentChildHierarchyCompatible(
      AccountType accountType,
      AccountTaxonomy parentAccountTaxonomy,
      AccountTaxonomy childAccountTaxonomy) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(parentAccountTaxonomy, "parentAccountTaxonomy");
    Objects.requireNonNull(childAccountTaxonomy, "childAccountTaxonomy");
    return ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(accountType)
        ? parentAccountTaxonomy
            .profitAndLossLineClassification()
            .equals(childAccountTaxonomy.profitAndLossLineClassification())
        : parentAccountTaxonomy
                .financialPositionLineClassification()
                .equals(childAccountTaxonomy.financialPositionLineClassification())
            && parentAccountTaxonomy
                .cashFlowAssetClassification()
                .equals(childAccountTaxonomy.cashFlowAssetClassification());
  }

  /** Returns whether a contra relationship preserves the target account's statement meaning. */
  public static boolean contraRelationshipCompatible(
      AccountType accountType,
      AccountTaxonomy targetAccountTaxonomy,
      AccountTaxonomy contraAccountTaxonomy) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(targetAccountTaxonomy, "targetAccountTaxonomy");
    Objects.requireNonNull(contraAccountTaxonomy, "contraAccountTaxonomy");
    return (accountType == AccountType.REVENUE
            && targetAccountTaxonomy
                .profitAndLossLineClassification()
                .equals(Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE))
            && contraAccountTaxonomy
                .profitAndLossLineClassification()
                .equals(Optional.of(ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE)))
        || parentChildHierarchyCompatible(
            accountType, targetAccountTaxonomy, contraAccountTaxonomy);
  }
}
