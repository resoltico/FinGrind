package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Canonical chart hierarchy and statement-line taxonomy for one declared account. */
public record AccountTaxonomy(
    AccountNodeKind nodeKind,
    Optional<AccountCode> parentAccountCode,
    Optional<FinancialPositionLineClassification> financialPositionLineClassification,
    Optional<ProfitAndLossLineClassification> profitAndLossLineClassification) {
  /** Validates one account taxonomy. */
  public AccountTaxonomy {
    Objects.requireNonNull(nodeKind, "nodeKind");
    Objects.requireNonNull(parentAccountCode, "parentAccountCode");
    Objects.requireNonNull(
        financialPositionLineClassification, "financialPositionLineClassification");
    Objects.requireNonNull(profitAndLossLineClassification, "profitAndLossLineClassification");
  }

  /** Returns the canonical empty taxonomy before account-type-specific validation. */
  public static AccountTaxonomy empty() {
    return new AccountTaxonomy(
        AccountNodeKind.POSTABLE, Optional.empty(), Optional.empty(), Optional.empty());
  }
}
