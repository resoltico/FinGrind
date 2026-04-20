package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Shared row and text helpers for query/report human and CSV renderers. */
final class CliQueryOutputSupport {
  private CliQueryOutputSupport() {}

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
        balance.netAmount().currencyCode().value(),
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
        row.balance().netAmount().currencyCode().value(),
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
        entry.movement().netAmount().currencyCode().value(),
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
        row.movement().netAmount().currencyCode().value(),
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
                balance.netAmount().currencyCode().value()
                    + " "
                    + displayMoney(balance.netAmount())
                    + " "
                    + balance.balanceSide().wireValue())
        .collect(Collectors.joining(", "));
  }

  static String displayMoney(Money money) {
    return CliTextFormat.displayAmount(money.currencyCode().value(), money.amount());
  }

  static String dateRange(
      Optional<LocalDate> effectiveDateFrom, Optional<LocalDate> effectiveDateTo) {
    return effectiveDateFrom.map(LocalDate::toString).orElse("(start)")
        + " to "
        + effectiveDateTo.map(LocalDate::toString).orElse("(current)");
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
    return postingFact.journalEntry().lines().getFirst().amount().currencyCode().value();
  }

  private static String postingTotalAmount(PostingFact postingFact) {
    JournalLine firstLine = postingFact.journalEntry().lines().getFirst();
    BigDecimal debitTotal =
        postingFact.journalEntry().lines().stream()
            .filter(line -> line.side() == JournalLine.EntrySide.DEBIT)
            .map(line -> line.amount().amount())
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return CliTextFormat.displayAmount(firstLine.amount().currencyCode().value(), debitTotal);
  }
}
