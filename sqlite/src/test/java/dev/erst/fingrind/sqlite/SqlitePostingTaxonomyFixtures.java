package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;

/** Shared taxonomy helpers for SQLite posting fixtures that need explicit balance ownership. */
final class SqlitePostingTaxonomyFixtures {
  private SqlitePostingTaxonomyFixtures() {}

  static AccountTaxonomy accountTaxonomy(AccountType accountType, NormalBalance normalBalance) {
    return switch (accountType) {
      case ASSET -> assetTaxonomy(normalBalance);
      case LIABILITY -> liabilityTaxonomy(normalBalance);
      case EQUITY -> equityTaxonomy(normalBalance);
      case REVENUE -> revenueTaxonomy(normalBalance);
      case EXPENSE -> expenseTaxonomy(normalBalance);
    };
  }

  private static AccountTaxonomy assetTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> SqlitePostingFactFixtureSupport.accountTaxonomy(AccountType.ASSET);
      case CREDIT ->
          throw new IllegalArgumentException(
              "ASSET tests must name an explicit contra-owning taxonomy, not credit-normal polarity.");
    };
  }

  private static AccountTaxonomy liabilityTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case CREDIT -> SqlitePostingFactFixtureSupport.accountTaxonomy(AccountType.LIABILITY);
      case DEBIT ->
          throw new IllegalArgumentException(
              "LIABILITY tests must name an explicit contra-owning taxonomy, not debit-normal polarity.");
    };
  }

  private static AccountTaxonomy equityTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case CREDIT -> SqlitePostingFactFixtureSupport.accountTaxonomy(AccountType.EQUITY);
      case DEBIT ->
          SqlitePostingFactFixtureSupport.financialPositionTaxonomy(
              FinancialPositionLineClassification.EQUITY_WITHDRAWAL);
    };
  }

  private static AccountTaxonomy revenueTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case CREDIT -> SqlitePostingFactFixtureSupport.accountTaxonomy(AccountType.REVENUE);
      case DEBIT ->
          throw new IllegalArgumentException(
              "REVENUE tests must name an explicit profit-and-loss taxonomy, not debit-normal polarity.");
    };
  }

  private static AccountTaxonomy expenseTaxonomy(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> SqlitePostingFactFixtureSupport.accountTaxonomy(AccountType.EXPENSE);
      case CREDIT ->
          throw new IllegalArgumentException(
              "EXPENSE tests must name an explicit profit-and-loss taxonomy, not credit-normal polarity.");
    };
  }
}
