package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.DeclareAccountCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.List;
import java.util.Optional;

final class CliFuzzSyntheticAccountFixtureSupport {
  private CliFuzzSyntheticAccountFixtureSupport() {}

  static List<String> accountCodes(List<DeclareAccountCommand> commands) {
    return commands.stream().map(command -> command.accountCode().value()).toList();
  }

  static String hashedFallbackCodeForBucket(int bucket) {
    for (int candidate = 0; candidate < 10_000; candidate++) {
      String accountCode = "A" + candidate;
      if (Math.floorMod(accountCode.hashCode(), 5) == bucket) {
        return accountCode;
      }
    }
    throw new IllegalStateException(
        "No synthetic fallback account code found for bucket " + bucket);
  }

  static String zeroLeadingFallbackCode() {
    return "0fallback";
  }

  static AccountType expectedSyntheticAccountType(String accountCode) {
    char first = accountCode.charAt(0);
    if (Character.isDigit(first)) {
      return switch (first) {
        case '1' -> AccountType.ASSET;
        case '2' -> AccountType.LIABILITY;
        case '3' -> AccountType.EQUITY;
        case '4' -> AccountType.REVENUE;
        case '5', '6', '7', '8', '9' -> AccountType.EXPENSE;
        default -> hashedAccountType(accountCode);
      };
    }
    return hashedAccountType(accountCode);
  }

  static DeclareAccountCommand declaredAccountCommand(
      String accountCode,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy) {
    return new DeclareAccountCommand(
        new AccountCode(accountCode),
        new AccountName("Synthetic " + accountCode),
        accountType,
        accountRole,
        accountTaxonomy);
  }

  static AccountTaxonomy financialPositionTaxonomy(
      FinancialPositionLineClassification classification) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.of(classification),
        Optional.empty());
  }

  static AccountTaxonomy profitAndLossTaxonomy(ProfitAndLossLineClassification classification) {
    return new AccountTaxonomy(
        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
        Optional.empty(),
        Optional.empty(),
        Optional.of(classification));
  }

  private static AccountType hashedAccountType(String accountCode) {
    return switch (Math.floorMod(accountCode.hashCode(), 5)) {
      case 0 -> AccountType.ASSET;
      case 1 -> AccountType.LIABILITY;
      case 2 -> AccountType.EQUITY;
      case 3 -> AccountType.REVENUE;
      default -> AccountType.EXPENSE;
    };
  }
}
