package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.StatementLineKind;
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
        CliHumanDisplay.instant(postingFact.provenance().recordedAt()),
        postingFact.postingId().value(),
        displayPostingKind(postingFact.postingKind()),
        displayPostingRoleHuman(postingFact),
        postingCurrency(postingFact),
        postingDebitTotal(postingFact),
        postingCreditTotal(postingFact),
        postingAccounts(postingFact),
        reversalTargetHuman(postingFact));
  }

  static List<String> postingRegisterCsvRow(PostingFact postingFact) {
    return List.of(
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        reversalStateWireValue(postingFact),
        postingCurrency(postingFact),
        postingDebitTotal(postingFact),
        postingCreditTotal(postingFact),
        postingAccounts(postingFact),
        reversalTargetCsv(postingFact),
        postingSourceDocumentsCsv(postingFact),
        postingApprovalsCsv(postingFact));
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
        CliHumanDisplay.instant(entry.postingFact().provenance().recordedAt()),
        entry.postingFact().postingId().value(),
        displayPostingKind(entry.postingFact().postingKind()),
        displayPostingRoleHuman(entry.postingFact()),
        reversalTargetHuman(entry.postingFact()),
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
        entry.postingFact().postingKind().wireValue(),
        reversalStateWireValue(entry.postingFact()),
        reversalTargetCsv(entry.postingFact()),
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

  static String postingSourceDocumentsHuman(PostingFact postingFact) {
    return sourceDocumentLabels(postingFact.evidence());
  }

  static String postingApprovalsHuman(PostingFact postingFact) {
    return approvalLabels(postingFact.evidence());
  }

  static String postingSourceDocumentsCsv(PostingFact postingFact) {
    return evidenceJson(
        postingFact.evidence().sourceDocuments().stream()
            .map(
                sourceDocument ->
                    new SourceDocumentCsvValue(
                        sourceDocument.sourceDocumentId().value(),
                        sourceDocument.sourceDocumentType().value()))
            .toList());
  }

  static String postingApprovalsCsv(PostingFact postingFact) {
    return evidenceJson(
        postingFact.evidence().approvals().stream()
            .map(
                approval ->
                    new ApprovalCsvValue(
                        approval.approvalId().value(), approval.approvalType().value()))
            .toList());
  }

  static String joinedBalances(List<CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(CliQueryOutputFormatter::displayBalanceHuman)
        .collect(Collectors.joining(", "));
  }

  private static String sourceDocumentLabels(AccountingEvidence evidence) {
    return CliTextFormat.joined(
        evidence.sourceDocuments().stream()
            .map(CliQueryOutputFormatter::sourceDocumentLabel)
            .toList());
  }

  private static String approvalLabels(AccountingEvidence evidence) {
    if (evidence.approvals().isEmpty()) {
      return "(none)";
    }
    return CliTextFormat.joined(
        evidence.approvals().stream().map(CliQueryOutputFormatter::approvalLabel).toList());
  }

  private static String sourceDocumentLabel(SourceDocumentReference sourceDocument) {
    return sourceDocument.sourceDocumentType().value()
        + " "
        + sourceDocument.sourceDocumentId().value();
  }

  private static String approvalLabel(ApprovalReference approval) {
    return approval.approvalType().value() + " " + approval.approvalId().value();
  }

  private static String evidenceJson(List<?> values) {
    return CliWireJson.jsonText(values);
  }

  private record SourceDocumentCsvValue(String sourceDocumentId, String sourceDocumentType) {}

  private record ApprovalCsvValue(String approvalId, String approvalType) {}

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

  static String displayRowKind(StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> "Account";
      case CURRENT_PERIOD_RESULT -> "Current period result";
    };
  }

  static String displayStatementLineCode(String lineCode, StatementLineKind lineKind) {
    return switch (lineKind) {
      case DECLARED_ACCOUNT -> lineCode;
      case CURRENT_PERIOD_RESULT -> "(derived)";
    };
  }

  static String displayLineRole(Optional<AccountRole> lineRole) {
    return lineRole.map(CliQueryOutputFormatter::displayAccountRoleLabel).orElse("(derived)");
  }

  static String displayAccountRoleLabel(AccountRole accountRole) {
    return switch (accountRole) {
      case ORDINARY -> "Ordinary";
      case CONTRA -> "Contra";
    };
  }

  static String displayFinancialPositionLineClassification(
      FinancialPositionLineClassification lineClassification) {
    return switch (lineClassification) {
      case CURRENT_ASSET -> "Current asset";
      case NONCURRENT_ASSET -> "Non-current asset";
      case CURRENT_LIABILITY -> "Current liability";
      case NONCURRENT_LIABILITY -> "Non-current liability";
      case OWNER_CAPITAL -> "Owner capital";
      case OWNER_DRAWINGS -> "Owner drawings";
      case PARTNER_CAPITAL -> "Partner capital";
      case PARTNER_CURRENT -> "Partner current";
      case SHARE_CAPITAL -> "Share capital";
      case RETAINED_EARNINGS -> "Retained earnings";
      case ACCUMULATED_SURPLUS -> "Accumulated surplus";
      case RESERVE -> "Reserve";
      case CURRENT_PERIOD_RESULT -> "Current period result";
      case OTHER_EQUITY -> "Other equity";
    };
  }

  static String displayProfitAndLossLineClassification(
      ProfitAndLossLineClassification lineClassification) {
    return switch (lineClassification) {
      case OPERATING_REVENUE -> "Operating revenue";
      case OTHER_REVENUE -> "Other revenue";
      case FINANCE_INCOME -> "Finance income";
      case COST_OF_SALES -> "Cost of sales";
      case OPERATING_EXPENSE -> "Operating expense";
      case DEPRECIATION_AND_AMORTIZATION -> "Depreciation and amortization";
      case FINANCE_EXPENSE -> "Finance expense";
      case TAX_EXPENSE -> "Tax expense";
    };
  }

  static String displayNormalBalanceLabel(NormalBalance normalBalance) {
    return switch (normalBalance) {
      case DEBIT -> "Debit";
      case CREDIT -> "Credit";
    };
  }

  static String displayPostingCoverage(PostingCoverage postingCoverage) {
    return switch (postingCoverage) {
      case ALL_POSTING_KINDS -> "All posting kinds";
      case NON_CLOSING_POSTINGS -> "Non-closing postings";
    };
  }

  static String displayPostingKind(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case PERIOD_CLOSE -> "Period close";
      case OPENING_BALANCE -> "Opening balance";
    };
  }

  static String displayPostingRoleHuman(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "Reversal" : "Direct";
  }

  static String reversalStateWireValue(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "reversal" : "direct";
  }

  static String reversalTargetHuman(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("(not a reversal)");
  }

  static String reversalTargetCsv(PostingFact postingFact) {
    return postingFact
        .reversalReference()
        .map(reference -> reference.priorPostingId().value())
        .orElse("");
  }

  static String postingCurrencyHuman(PostingFact postingFact) {
    return postingCurrency(postingFact);
  }

  static String postingDebitTotalHuman(PostingFact postingFact) {
    return postingDebitTotal(postingFact);
  }

  static String postingCreditTotalHuman(PostingFact postingFact) {
    return postingCreditTotal(postingFact);
  }

  static String postingAccountsHuman(PostingFact postingFact) {
    return postingAccounts(postingFact);
  }

  static String lowerDateBoundaryMeaning(@Nullable LocalDate effectiveDateFrom) {
    return effectiveDateFrom == null ? "book-start" : "selected-date";
  }

  static String upperDateBoundaryMeaning(@Nullable LocalDate effectiveDateTo) {
    return effectiveDateTo == null ? "latest-committed-posting" : "selected-date";
  }

  static String lowerDateBoundaryLabel(@Nullable LocalDate effectiveDateFrom) {
    return CliHumanDisplay.lowerDateBoundary(effectiveDateFrom);
  }

  static String upperDateBoundaryLabel(@Nullable LocalDate effectiveDateTo) {
    return CliHumanDisplay.upperDateBoundary(effectiveDateTo);
  }

  static String displayBooleanLabel(boolean value) {
    return value ? "Yes" : "No";
  }

  static String displayMoney(Money money) {
    return CliTextFormat.displayMoney(money);
  }

  static String dateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    return CliHumanDisplay.dateRange(effectiveDateFrom, effectiveDateTo);
  }

  static String absolutePath(Path bookFilePath) {
    return CliHumanDisplay.path(bookFilePath);
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
