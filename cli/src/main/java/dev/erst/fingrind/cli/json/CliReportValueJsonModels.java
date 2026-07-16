package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import java.util.Objects;

/** Reusable value objects shared by semantic machine report payloads. */
public interface CliReportValueJsonModels {
  /** Exact non-negative money value; minor units stay textual to preserve integer precision. */
  record MoneyPayload(String currencyCode, String minorUnits) {
    public MoneyPayload {
      currencyCode = requireText(currencyCode, "currencyCode");
      minorUnits = requireText(minorUnits, "minorUnits");
    }
  }

  /** One exact currency balance with an explicit net side. */
  record BalancePayload(
      String currencyCode,
      MoneyPayload debitTotal,
      MoneyPayload creditTotal,
      MoneyPayload netAmount,
      String balanceSide) {
    public BalancePayload {
      currencyCode = requireText(currencyCode, "currencyCode");
      Objects.requireNonNull(debitTotal, "debitTotal");
      Objects.requireNonNull(creditTotal, "creditTotal");
      Objects.requireNonNull(netAmount, "netAmount");
      balanceSide = requireText(balanceSide, "balanceSide");
    }
  }

  /** Canonical declared-account facts carried by account-derived report rows. */
  record AccountPayload(
      String accountCode,
      String accountName,
      String accountType,
      String normalBalance,
      boolean active) {
    public AccountPayload {
      accountCode = requireText(accountCode, "accountCode");
      accountName = requireText(accountName, "accountName");
      accountType = requireText(accountType, "accountType");
      normalBalance = requireText(normalBalance, "normalBalance");
    }
  }
}
