package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Shared row and text helpers for query/report human and CSV renderers. */
final class CliQueryOutputFormatter {
  private CliQueryOutputFormatter() {}

  static List<String> postingRegisterRow(PostingFact postingFact) {
    return List.of(
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingCurrency(postingFact),
        postingTotalAmount(postingFact),
        postingAccounts(postingFact),
        postingFact
            .reversalReference()
            .map(reference -> reference.priorPostingId().value())
            .orElse(""));
  }

  static List<String> balanceRow(CurrencyBalance balance) {
    return List.of(
        balance.netAmount().currencyUnit().code(),
        displayMoney(balance.debitTotal()),
        displayMoney(balance.creditTotal()),
        displayMoney(balance.netAmount()),
        balance.balanceSide().wireValue());
  }

  static List<String> trialBalanceRow(TrialBalanceRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().normalBalance().wireValue(),
        Boolean.toString(row.account().active()),
        row.balance().netAmount().currencyUnit().code(),
        displayMoney(row.balance().debitTotal()),
        displayMoney(row.balance().creditTotal()),
        displayMoney(row.balance().netAmount()),
        row.balance().balanceSide().wireValue());
  }

  static List<String> accountLedgerRow(DeclaredAccount account, AccountLedgerEntry entry) {
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

  static List<String> periodActivityRow(PeriodAccountActivityRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
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
        .map(
            balance ->
                balance.netAmount().currencyUnit().code()
                    + " "
                    + displayMoney(balance.netAmount())
                    + " "
                    + balance.balanceSide().wireValue())
        .collect(Collectors.joining(", "));
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

  private static String postingTotalAmount(PostingFact postingFact) {
    long debitTotalMinorUnits =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
            .mapToLong(line -> line.amount().minorUnits())
            .sum();
    return CliTextFormat.displayMoney(
        Money.ofMinorUnits(postingFact.journalEntry().currencyUnit(), debitTotalMinorUnits));
  }
}
