package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.SourceDocumentReference;
import java.util.List;

/** Formats posting-driven rows and evidence text for query/report output. */
final class CliPostingFactFormatter {
  private CliPostingFactFormatter() {}

  static List<String> postingRegisterTextRow(PostingFact postingFact) {
    return List.of(
        postingFact.journalEntry().effectiveDate().toString(),
        CliPostingLabels.displayPostingOriginKind(postingFact.postingOriginKind()),
        CliPostingLabels.displayPostingRoleText(postingFact),
        CliPostingLabels.postingDebitTotal(postingFact),
        CliPostingLabels.postingCreditTotal(postingFact),
        postingAccounts(postingFact),
        CliHumanDisplay.opaqueReference(postingFact.postingId().value()));
  }

  static List<String> trialBalanceCsvRow(TrialBalanceRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().accountType().wireValue(),
        row.account().normalBalance().wireValue(),
        Boolean.toString(row.account().active()),
        row.balance().netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(row.balance().debitTotal()),
        CliQueryScopeText.displayMoney(row.balance().creditTotal()),
        CliQueryScopeText.displayMoney(row.balance().netAmount()),
        row.balance().balanceSide().wireValue());
  }

  static List<String> accountLedgerTextRow(DeclaredAccount account, AccountLedgerEntry entry) {
    return List.of(
        entry.postingFact().journalEntry().effectiveDate().toString(),
        CliPostingLabels.displayPostingOriginKind(entry.postingFact().postingOriginKind()),
        CliQueryScopeText.displayMoney(entry.movement().debitTotal()),
        CliQueryScopeText.displayMoney(entry.movement().creditTotal()),
        CliQueryScopeText.displayMoney(entry.runningNetAmount())
            + " "
            + CliBalanceOutputFormatter.displayBalanceSideLabel(entry.runningBalanceSide()),
        counterpartAccounts(account, entry.postingFact()),
        CliHumanDisplay.opaqueReference(entry.postingFact().postingId().value()));
  }

  static List<String> periodActivityCsvRow(PeriodAccountActivityRow row) {
    return List.of(
        row.account().accountCode().value(),
        row.account().accountName().value(),
        row.account().accountType().wireValue(),
        row.account().normalBalance().wireValue(),
        row.movement().netAmount().currencyUnit().code(),
        CliQueryScopeText.displayMoney(row.movement().debitTotal()),
        CliQueryScopeText.displayMoney(row.movement().creditTotal()),
        CliQueryScopeText.displayMoney(row.movement().netAmount()),
        row.movement().balanceSide().wireValue());
  }

  static String counterpartAccounts(DeclaredAccount account, PostingFact postingFact) {
    List<String> counterparts =
        postingFact.journalEntry().lines().stream()
            .map(line -> line.accountCode().value())
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

  private static String sourceDocumentLabels(AccountingEvidence evidence) {
    return CliTextFormat.joined(
        evidence.sourceDocuments().stream()
            .map(CliPostingFactFormatter::sourceDocumentLabel)
            .toList());
  }

  private static String approvalLabels(AccountingEvidence evidence) {
    if (evidence.approvals().isEmpty()) {
      return "(none)";
    }
    return CliTextFormat.joined(
        evidence.approvals().stream().map(CliPostingFactFormatter::approvalLabel).toList());
  }

  private static String sourceDocumentLabel(SourceDocumentReference sourceDocument) {
    return sourceDocument.sourceDocumentType().value()
        + " "
        + sourceDocument.sourceDocumentId().value()
        + " on "
        + sourceDocument.documentDate();
  }

  private static String approvalLabel(ApprovalReference approval) {
    return approval.approvalType().value()
        + " "
        + approval.approvalId().value()
        + " by "
        + approval.approverType()
        + " "
        + approval.approverReference()
        + " "
        + approval.decision().wireValue();
  }

  private static String postingAccounts(PostingFact postingFact) {
    return CliTextFormat.joined(
        postingFact.journalEntry().lines().stream()
            .map(line -> line.accountCode().value())
            .distinct()
            .toList());
  }
}
