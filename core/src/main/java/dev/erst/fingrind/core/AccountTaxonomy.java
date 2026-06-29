package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Canonical chart hierarchy and statement-line taxonomy for one declared account. */
public record AccountTaxonomy(
    AccountNodeKind nodeKind,
    Optional<AccountCode> parentAccountCode,
    Optional<FinancialPositionLineClassification> financialPositionLineClassification,
    Optional<ProfitAndLossLineClassification> profitAndLossLineClassification,
    Optional<CashFlowAssetClassification> cashFlowAssetClassification) {
  /** Validates one account taxonomy. */
  public AccountTaxonomy {
    Objects.requireNonNull(nodeKind, "nodeKind");
    Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    Objects.requireNonNull(
        financialPositionLineClassification, "financialPositionLineClassification");
    Objects.requireNonNull(profitAndLossLineClassification, "profitAndLossLineClassification");
    Objects.requireNonNull(cashFlowAssetClassification, "cashFlowAssetClassification");
  }

  /** Convenience constructor for taxonomies that omit a cash-flow asset classification. */
  public AccountTaxonomy(
      AccountNodeKind nodeKind,
      Optional<AccountCode> parentAccountCode,
      Optional<FinancialPositionLineClassification> financialPositionLineClassification,
      Optional<ProfitAndLossLineClassification> profitAndLossLineClassification) {
    this(
        nodeKind,
        parentAccountCode,
        financialPositionLineClassification,
        profitAndLossLineClassification,
        Optional.empty());
  }

  /** Returns the canonical empty taxonomy before account-type-specific validation. */
  public static AccountTaxonomy empty() {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty());
  }
}
