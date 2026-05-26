package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountNodeKind;
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
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.StatementLineKind;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Shared row and text helpers for query/report text and CSV renderers. */
final class CliQueryOutputFormatter {
  private CliQueryOutputFormatter() {}

  static List<String> postingRegisterTextRow(PostingFact postingFact) {
    return List.of(
        postingFact.journalEntry().effectiveDate().toString(),
        displayPostingOriginKind(postingFact.postingOriginKind()),
        displayPostingRoleText(postingFact),
        postingDebitTotal(postingFact),
        postingCreditTotal(postingFact),
        postingAccounts(postingFact),
        postingFact.postingId().value());
  }

  static List<String> postingRegisterCsvRow(PostingFact postingFact) {
    return List.of(
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        reversalStateWireValue(postingFact),
        postingCurrency(postingFact),
        postingDebitTotal(postingFact),
        postingCreditTotal(postingFact),
        postingAccounts(postingFact),
        reversalTargetCsv(postingFact),
        postingSourceDocumentIdsCsv(postingFact),
        postingSourceDocumentTypesCsv(postingFact),
        postingApprovalIdsCsv(postingFact),
        postingApprovalDecisionsCsv(postingFact));
  }

  static List<String> balanceTextRow(CurrencyBalance balance) {
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

  static List<String> accountLedgerTextRow(DeclaredAccount account, AccountLedgerEntry entry) {
    return List.of(
        entry.postingFact().journalEntry().effectiveDate().toString(),
        displayPostingOriginKind(entry.postingFact().postingOriginKind()),
        displayMoney(entry.movement().debitTotal()),
        displayMoney(entry.movement().creditTotal()),
        displayMoney(entry.runningNetAmount())
            + " "
            + displayBalanceSideLabel(entry.runningBalanceSide()),
        counterpartAccounts(account, entry.postingFact()),
        entry.postingFact().postingId().value());
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

  static String postingSourceDocumentsText(PostingFact postingFact) {
    return sourceDocumentLabels(postingFact.evidence());
  }

  static String postingSourceDocumentIdsText(PostingFact postingFact) {
    return CliTextFormat.joined(
        postingFact.evidence().sourceDocuments().stream()
            .map(sourceDocument -> sourceDocument.sourceDocumentId().value())
            .toList());
  }

  static String postingApprovalsText(PostingFact postingFact) {
    return approvalLabels(postingFact.evidence());
  }

  static String postingSourceDocumentIdsCsv(PostingFact postingFact) {
    return csvCellList(
        postingFact.evidence().sourceDocuments().stream()
            .map(sourceDocument -> sourceDocument.sourceDocumentId().value())
            .toList());
  }

  static String postingSourceDocumentTypesCsv(PostingFact postingFact) {
    return csvCellList(
        postingFact.evidence().sourceDocuments().stream()
            .map(sourceDocument -> sourceDocument.sourceDocumentType().value())
            .toList());
  }

  static String postingApprovalIdsCsv(PostingFact postingFact) {
    return csvCellList(
        postingFact.evidence().approvals().stream()
            .map(approval -> approval.approvalId().value())
            .toList());
  }

  static String postingApprovalDecisionsCsv(PostingFact postingFact) {
    return csvCellList(
        postingFact.evidence().approvals().stream()
            .map(approval -> approval.decision().wireValue())
            .toList());
  }

  static String joinedBalances(List<CurrencyBalance> balances) {
    if (balances.isEmpty()) {
      return "(none)";
    }
    return balances.stream()
        .map(CliQueryOutputFormatter::displayBalanceText)
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
        + sourceDocument.sourceDocumentId().value()
        + " on "
        + sourceDocument.documentDate()
        + " at "
        + sourceDocument.storageLocator().value();
  }

  private static String approvalLabel(ApprovalReference approval) {
    return approval.approvalType().value()
        + " "
        + approval.approvalId().value()
        + " by "
        + approval.approverType().wireValue()
        + " "
        + approval.approverId().value()
        + " "
        + approval.decision().wireValue();
  }

  private static String csvCellList(List<String> values) {
    return values.isEmpty() ? "" : String.join("; ", values);
  }

  static String displayBalance(CurrencyBalance balance) {
    return balance.netAmount().currencyUnit().code()
        + " "
        + displayMoney(balance.netAmount())
        + " "
        + balance.balanceSide().wireValue();
  }

  static String displayBalanceText(CurrencyBalance balance) {
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
      case ZERO -> "Zero";
    };
  }

  static String displayBalanceStateLabel(boolean balanced) {
    return balanced ? "Balanced" : "Imbalanced";
  }

  static String noMatchesLabel(String subjectPlural) {
    return "No " + subjectPlural + " matched the selected scope.";
  }

  static String zeroAcrossCurrenciesLabel() {
    return "Zero across all currencies.";
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

  static String displayAccountNodeKindLabel(AccountNodeKind nodeKind) {
    return switch (nodeKind) {
      case HEADER -> "Header";
      case POSTABLE -> "Postable";
    };
  }

  static String displayFinancialPositionLineClassification(
      FinancialPositionLineClassification lineClassification) {
    return switch (lineClassification) {
      case CURRENT_ASSET -> "Current asset";
      case NONCURRENT_ASSET -> "Non-current asset";
      case CURRENT_LIABILITY -> "Current liability";
      case NONCURRENT_LIABILITY -> "Non-current liability";
      case EQUITY_CONTRIBUTION -> "Contributed capital";
      case EQUITY_WITHDRAWAL -> "Distributions";
      case RESULT_HOLDING -> "Accumulated result";
      case RESERVE -> "Reserve";
      case OTHER_EQUITY -> "Other equity";
    };
  }

  static String displayFinancialPositionLineClassification(
      Optional<FinancialPositionLineClassification> lineClassification) {
    return lineClassification
        .map(CliQueryOutputFormatter::displayFinancialPositionLineClassification)
        .orElse("(derived)");
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
      case OTHER_EXPENSE -> "Other expense";
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
      case NON_CLOSING_POSTINGS -> "Non-transfer postings";
    };
  }

  static String displayPostingKind(PostingKind postingKind) {
    return switch (postingKind) {
      case STANDARD -> "Standard";
      case PERIOD_RESULT_TRANSFER -> "Period result transfer";
      case OPENING_BALANCE -> "Opening balance";
    };
  }

  static String displayPostingOriginKind(PostingOriginKind postingOriginKind) {
    return switch (postingOriginKind) {
      case CASH_REVENUE -> "Cash revenue";
      case CASH_EXPENSE -> "Cash expense";
      case EQUITY_CONTRIBUTION -> "Equity contribution";
      case EQUITY_WITHDRAWAL -> "Equity withdrawal";
      case OPENING_BALANCE_ADJUSTMENT -> "Opening balance";
      case CORRECTION_ADJUSTMENT -> "Correction";
      case REVERSAL_ADJUSTMENT -> "Reversal adjustment";
      case PERIOD_RESULT_TRANSFER -> "Result transfer";
    };
  }

  static String displayPostingRoleText(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "Reversal" : "Direct";
  }

  static String reversalStateWireValue(PostingFact postingFact) {
    return postingFact.reversalReference().isPresent() ? "reversal" : "direct";
  }

  static String reversalTargetText(PostingFact postingFact) {
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

  static String lowerDateBoundaryMeaning(@Nullable LocalDate effectiveDateFrom) {
    return effectiveDateFrom == null ? "book-start" : "selected-date";
  }

  static String upperDateBoundaryMeaning(@Nullable LocalDate effectiveDateTo) {
    return effectiveDateTo == null ? "latest-committed-posting" : "selected-date";
  }

  static String lowerDateBoundaryLabel(@Nullable LocalDate effectiveDateFrom) {
    return CliTextDisplay.lowerDateBoundary(effectiveDateFrom);
  }

  static String upperDateBoundaryLabel(@Nullable LocalDate effectiveDateTo) {
    return CliTextDisplay.upperDateBoundary(effectiveDateTo);
  }

  static String displayBooleanLabel(boolean value) {
    return value ? "Yes" : "No";
  }

  static String displayMoney(Money money) {
    return CliTextFormat.displayMoney(money);
  }

  static String dateRange(
      @Nullable LocalDate effectiveDateFrom, @Nullable LocalDate effectiveDateTo) {
    return CliTextDisplay.dateRange(effectiveDateFrom, effectiveDateTo);
  }

  static String absolutePath(Path bookFilePath) {
    return CliTextDisplay.path(bookFilePath);
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
