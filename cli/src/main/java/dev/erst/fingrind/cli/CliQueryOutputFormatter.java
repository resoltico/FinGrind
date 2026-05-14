package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Shared row and text helpers for query/report human and CSV renderers. */
final class CliQueryOutputFormatter {
  private CliQueryOutputFormatter() {}

  static List<String> postingRegisterHumanRow(PostingFact postingFact) {
    return List.of(
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingCurrency(postingFact),
        postingDebitTotal(postingFact),
        postingCreditTotal(postingFact),
        postingAccounts(postingFact),
        postingFact
            .reversalReference()
            .map(reference -> reference.priorPostingId().value())
            .orElse(""));
  }

  static List<String> postingRegisterCsvRow(PostingFact postingFact) {
    return postingRegisterHumanRow(postingFact);
  }

  static List<String> balanceHumanRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        displayMoney(balance.debitTotal()),
        displayMoney(balance.creditTotal()),
        displayMoney(balance.netAmount()),
        displayBalanceSideLabel(balance.balanceSide()));
  }

  static List<String> balanceCsvRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        displayMoney(balance.debitTotal()),
        displayMoney(balance.creditTotal()),
        displayMoney(balance.netAmount()),
        balance.balanceSide().wireValue());
  }

  static List<String> trialBalanceHumanRow(TrialBalanceRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        displayLineTypeLabel(row.account().accountType()),
        displayAccountRoleLabel(row.account().accountRole()),
        displayNormalBalanceLabel(row.account().normalBalance()),
        displayBooleanLabel(row.account().active()),
        row.balance().netAmount().currencyUnit().code(),
        displayMoney(row.balance().debitTotal()),
        displayMoney(row.balance().creditTotal()),
        displayMoney(row.balance().netAmount()),
        displayBalanceSideLabel(row.balance().balanceSide()));
  }

  static List<String> trialBalanceCsvRow(TrialBalanceRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().accountType().wireValue(),
        row.account().accountRole().wireValue(),
        row.account().normalBalance().wireValue(),
        Boolean.toString(row.account().active()),
        row.balance().netAmount().currencyUnit().code(),
        displayMoney(row.balance().debitTotal()),
        displayMoney(row.balance().creditTotal()),
        displayMoney(row.balance().netAmount()),
        row.balance().balanceSide().wireValue());
  }

  static List<String> accountLedgerHumanRow(DeclaredAccount account, AccountLedgerEntry entry) {
    return List.of(
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        entry.postingFact().postingId().value(),
        entry.movement().netAmount().currencyUnit().code(),
        displayMoney(entry.movement().debitTotal()),
        displayMoney(entry.movement().creditTotal()),
        displayMoney(entry.runningNetAmount()),
        displayBalanceSideLabel(entry.runningBalanceSide()),
        counterpartAccounts(account, entry.postingFact()));
  }

  static List<String> accountLedgerCsvRow(DeclaredAccount account, AccountLedgerEntry entry) {
    return List.of(
        entry.postingFact().journalEntry().effectiveDate().toString(),
        entry.postingFact().provenance().recordedAt().toString(),
        entry.postingFact().postingId().value(),
        entry.movement().netAmount().currencyUnit().code(),
        displayMoney(entry.movement().debitTotal()),
        displayMoney(entry.movement().creditTotal()),
        displayMoney(entry.runningNetAmount()),
        entry.runningBalanceSide().wireValue(),
        counterpartAccounts(account, entry.postingFact()));
  }

  static List<String> periodActivityHumanRow(PeriodAccountActivityRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        displayLineTypeLabel(row.account().accountType()),
        displayAccountRoleLabel(row.account().accountRole()),
        displayNormalBalanceLabel(row.account().normalBalance()),
        row.movement().netAmount().currencyUnit().code(),
        displayMoney(row.movement().debitTotal()),
        displayMoney(row.movement().creditTotal()),
        displayMoney(row.movement().netAmount()),
        displayBalanceSideLabel(row.movement().balanceSide()));
  }

  static List<String> periodActivityCsvRow(PeriodAccountActivityRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().accountType().wireValue(),
        row.account().accountRole().wireValue(),
        row.account().normalBalance().wireValue(),
        row.movement().netAmount().currencyUnit().code(),
        displayMoney(row.movement().debitTotal()),
        displayMoney(row.movement().creditTotal()),
        displayMoney(row.movement().netAmount()),
        row.movement().balanceSide().wireValue());
  }

  static String counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    List<String> counterparts =
        postingFact.journalEntry().lines().stream()
            .map(JournalLine::accountCode)
            .map(accountCode -> accountCode.value())
            .filter(accountCode -> !accountCode.equals(account.accountCode().value()))
            .distinct()
            .toList();
    return counterparts.isEmpty() ? "(self)" : CliTextFormat.joined(counterparts);
  }

  static String joinedBalances(List<CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(CliQueryOutputFormatter::displayBalance)
        .collect(Collectors.joining(", "));
  }

  static String displayBalance(CurrencyBalance balance) {
    return balance.netAmount().currencyUnit().code()
        + " "
        + displayMoney(balance.netAmount())
        + " "
        + balance.balanceSide().wireValue();
  }

  static String displayBalanceHuman(CurrencyBalance balance) {
    return balance.netAmount().currencyUnit().code()
        + " "
        + displayMoney(balance.netAmount())
        + " "
        + displayBalanceSideLabel(balance.balanceSide());
  }

  static String displayBalanceSideLabel(BalanceSide balanceSide) {
    return switch (balanceSide) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
      case ZERO -> "Balanced";
    };
  }

  static String displayAccountTypeSectionLabel(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Assets";
      case LIABILITY -> "Liabilities";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expenses";
    };
  }

  static String displayLineTypeLabel(AccountType accountType) {
    return switch (accountType) {
      case ASSET -> "Asset";
      case LIABILITY -> "Liability";
      case EQUITY -> "Equity";
      case REVENUE -> "Revenue";
      case EXPENSE -> "Expense";
    };
  }

  static String displayRowKind(boolean synthetic) {
    return synthetic ? "Synthetic" : "Account";
  }

  static String displayLineRole(Optional<AccountRole> lineRole) {
    return lineRole.map(CliQueryOutputFormatter::displayAccountRoleLabel).orElse("(derived)");
  }

  static String displayAccountRoleLabel(AccountRole accountRole) {
    return switch (accountRole) {
      case ORDINARY -> "Ordinary";
      case CONTRA -> "Contra";
      case RETAINED_EARNINGS -> "Retained earnings";
    };
  }

  static String displayNormalBalanceLabel(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
    };
  }

  static String displayBooleanLabel(boolean value) {
    return value ? "Yes" : "No";
  }

  static String displayMoney(Money money) {
    return CliTextFormat.displayMoney(money);
  }

  static String dateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    return (effectiveDateFrom == null ? "(start)" : effectiveDateFrom.toString())
        + " to "
        + (effectiveDateTo == null ? "(current)" : effectiveDateTo.toString());
  }

  static String absolutePath(Path bookFilePath) {
    return bookFilePath.toAbsolutePath().normalize().toString();
  }

  private static String postingAccounts(PostingFact postingFact) {
    return CliTextFormat.joined(
        postingFact.journalEntry().lines().stream()
            .map(line -> line.accountCode().value())
            .distinct()
            .toList());
  }

  private static String postingCurrency(PostingFact postingFact) {
    return postingFact.journalEntry().currencyUnit().code();
  }

  private static String postingDebitTotal(PostingFact postingFact) {
    long debitTotalMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return CliTextFormat.displayMoney(
        Money.ofMinorUnits(postingFact.journalEntry().currencyUnit(), debitTotalMinorUnits));
  }

  private static String postingCreditTotal(PostingFact postingFact) {
    long creditTotalMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.CREDIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return CliTextFormat.displayMoney(
        Money.ofMinorUnits(postingFact.journalEntry().currencyUnit(), creditTotalMinorUnits));
  }
}
