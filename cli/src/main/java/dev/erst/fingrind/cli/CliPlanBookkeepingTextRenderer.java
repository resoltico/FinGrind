package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Renders bookkeeping and query payloads embedded in full execute-plan journal output. */
final class CliPlanBookkeepingTextRenderer {
  private CliPlanBookkeepingTextRenderer() {}

  static String renderDeclaredAccount(
      String outcome, CliBookQueryJsonModels.DeclaredAccountPayload account) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("Outcome", outcome));
    rows.add(List.of("Account code", account.accountCode()));
    rows.add(List.of("Account name", account.accountName()));
    rows.add(List.of("Account type", CliTextDisplay.wireLabel(account.accountType())));
    rows.add(List.of("Node kind", CliTextDisplay.wireLabel(account.accountNodeKind())));
    rows.add(List.of("Parent account", displayOrNone(account.parentAccountCode())));
    rows.add(
        List.of(
            "Financial position classification",
            displayOrNone(account.financialPositionLineClassification())));
    rows.add(
        List.of(
            "Profit and loss classification",
            displayOrNone(account.profitAndLossLineClassification())));
    rows.add(List.of("Normal balance", CliTextDisplay.wireLabel(account.normalBalance())));
    rows.add(List.of("Active", CliQueryScopeText.displayBooleanLabel(account.active())));
    rows.add(List.of("Declared at", account.declaredAt()));
    return CliTextFormat.renderKeyValueBlock(List.copyOf(rows));
  }

  static String renderAccountPage(CliPlanJsonModels.AccountPageStepDataPayload accountPage) {
    List<List<String>> summaryRows =
        List.of(
            List.of("Count", Integer.toString(accountPage.count())),
            List.of("Page limit", Integer.toString(accountPage.pageLimit())),
            List.of("Next cursor", displayOrNone(accountPage.nextCursor())),
            List.of("Has more", CliQueryScopeText.displayBooleanLabel(accountPage.hasMore())));
    String accountsTable =
        accountPage.accounts().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("accounts")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of("Account code", "Account name", "Type", "Normal balance", "Active"),
                accountPage.accounts().stream()
                    .map(
                        account ->
                            List.of(
                                account.accountCode(),
                                account.accountName(),
                                CliTextDisplay.wireLabel(account.accountType()),
                                CliTextDisplay.wireLabel(account.normalBalance()),
                                CliQueryScopeText.displayBooleanLabel(account.active())))
                    .toList());
    return CliReportRenderSupport.joinSections(
        CliTextFormat.renderKeyValueBlock(summaryRows),
        CliReportRenderSupport.section("Accounts", accountsTable));
  }

  static String renderPosting(CliBookQueryJsonModels.PostingPayload posting) {
    List<List<String>> summaryRows = new ArrayList<>();
    summaryRows.add(List.of("Posting id", posting.postingId()));
    summaryRows.add(List.of("Posting kind", CliTextDisplay.wireLabel(posting.postingKind())));
    summaryRows.add(
        List.of("Posting origin kind", CliTextDisplay.wireLabel(posting.postingOriginKind())));
    summaryRows.add(List.of("Reversal state", CliTextDisplay.wireLabel(posting.reversalState())));
    summaryRows.add(List.of("Effective date", posting.effectiveDate()));
    summaryRows.add(List.of("Recorded at", posting.recordedAt()));
    summaryRows.add(List.of("Actor id", posting.actorId()));
    summaryRows.add(List.of("Actor type", CliTextDisplay.wireLabel(posting.actorType())));
    summaryRows.add(List.of("Command id", posting.commandId()));
    summaryRows.add(List.of("Idempotency key", posting.idempotencyKey()));
    summaryRows.add(List.of("Causation id", posting.causationId()));
    summaryRows.add(List.of("Correlation id", displayOrNone(posting.correlationId())));
    summaryRows.add(List.of("Source channel", CliTextDisplay.wireLabel(posting.sourceChannel())));
    String linesTable = CliJournalLineTextRenderer.renderPayloadLines(posting.lines());
    List<String> sections = new ArrayList<>();
    sections.add(CliTextFormat.renderKeyValueBlock(List.copyOf(summaryRows)));
    if (posting.entry() != null) {
      sections.add(
          CliReportRenderSupport.section(
              "Entry facts", CliPostingEntryPayloadSupport.renderEntryFacts(posting.entry())));
    }
    sections.add(CliReportRenderSupport.section("Journal lines", linesTable));
    sections.add(CliReportRenderSupport.section("Evidence", renderEvidence(posting.evidence())));
    if (posting.reversal() != null) {
      sections.add(CliReportRenderSupport.section("Reversal", renderReversal(posting.reversal())));
    }
    return CliReportRenderSupport.joinSections(sections.toArray(String[]::new));
  }

  static String renderPostingPage(CliPlanJsonModels.PostingPageStepDataPayload postingPage) {
    List<List<String>> summaryRows =
        List.of(
            List.of("Count", Integer.toString(postingPage.count())),
            List.of("Page limit", Integer.toString(postingPage.pageLimit())),
            List.of("Next cursor", displayOrNone(postingPage.nextCursor())),
            List.of("Has more", CliQueryScopeText.displayBooleanLabel(postingPage.hasMore())));
    String postingsTable =
        postingPage.postings().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("postings")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of(
                    "Posting id",
                    "Effective date",
                    "Posting kind",
                    "Reversal",
                    "Debit",
                    "Credit",
                    "Accounts"),
                postingPage.postings().stream()
                    .map(
                        posting ->
                            List.of(
                                posting.postingId(),
                                posting.effectiveDate(),
                                CliTextDisplay.wireLabel(posting.postingKind()),
                                CliTextDisplay.wireLabel(posting.reversalState()),
                                posting.debitTotal().canonicalDecimal(),
                                posting.creditTotal().canonicalDecimal(),
                                CliTextFormat.joined(posting.accountCodes())))
                    .toList(),
                4,
                5);
    return CliReportRenderSupport.joinSections(
        CliTextFormat.renderKeyValueBlock(summaryRows),
        CliReportRenderSupport.section("Postings", postingsTable));
  }

  static String renderAccountBalance(
      CliPlanJsonModels.AccountBalanceStepDataPayload accountBalance) {
    List<List<String>> summaryRows = new ArrayList<>();
    summaryRows.add(
        List.of(
            "Account",
            CliHumanDisplay.accountLabel(
                accountBalance.account().accountCode(), accountBalance.account().accountName())));
    summaryRows.add(
        List.of("Effective date from", displayOrNone(accountBalance.effectiveDateFrom())));
    summaryRows.add(List.of("Effective date to", displayOrNone(accountBalance.effectiveDateTo())));
    summaryRows.add(List.of("Balance buckets", Integer.toString(accountBalance.bucketCount())));
    String balancesTable =
        accountBalance.balances().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("balances")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of("Currency", "Debit total", "Credit total", "Net amount", "Balance side"),
                accountBalance.balances().stream()
                    .map(
                        balance ->
                            List.of(
                                balance.netAmount().currencyCode(),
                                balance.debitTotal().canonicalDecimal(),
                                balance.creditTotal().canonicalDecimal(),
                                balance.netAmount().canonicalDecimal(),
                                CliTextDisplay.wireLabel(balance.balanceSide())))
                    .toList(),
                1,
                2,
                3);
    return CliReportRenderSupport.joinSections(
        CliTextFormat.renderKeyValueBlock(List.copyOf(summaryRows)),
        CliReportRenderSupport.section("Balances", balancesTable));
  }

  private static String renderEvidence(CliBookQueryJsonModels.AccountingEvidencePayload evidence) {
    List<String> sections = new ArrayList<>();
    String sourceDocumentsTable =
        evidence.sourceDocuments().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("source documents")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of("Source document id", "Type", "Document date"),
                evidence.sourceDocuments().stream()
                    .map(
                        document ->
                            List.of(
                                document.sourceDocumentId(),
                                CliTextDisplay.wireLabel(document.sourceDocumentType()),
                                document.documentDate()))
                    .toList());
    sections.add(CliReportRenderSupport.section("Source documents", sourceDocumentsTable));
    String approvalsTable =
        evidence.approvals().isEmpty()
            ? CliQueryScopeText.noMatchesLabel("approvals")
            : CliTextFormat.renderAdaptiveTable(
                CliReportRenderSupport.TEXT_TABLE_WIDTH,
                List.of(
                    "Approval id",
                    "Type",
                    "Approver id",
                    "Approver type",
                    "Decision",
                    "Approved at"),
                evidence.approvals().stream()
                    .map(
                        approval ->
                            List.of(
                                approval.approvalId(),
                                CliTextDisplay.wireLabel(approval.approvalType()),
                                approval.approverId(),
                                CliTextDisplay.wireLabel(approval.approverType()),
                                CliTextDisplay.wireLabel(approval.decision()),
                                approval.approvedAt()))
                    .toList());
    sections.add(CliReportRenderSupport.section("Approvals", approvalsTable));
    return CliReportRenderSupport.joinSections(sections.toArray(String[]::new));
  }

  private static String renderReversal(CliBookQueryJsonModels.ReversalPayload reversal) {
    return CliTextFormat.renderKeyValueBlock(
        List.of(
            List.of("Prior posting id", reversal.priorPostingId()),
            List.of("Reason", reversal.reason())));
  }

  private static String displayOrNone(@Nullable String value) {
    return value == null ? "(none)" : value;
  }
}
