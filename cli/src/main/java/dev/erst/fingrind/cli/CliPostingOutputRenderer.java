package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import java.util.ArrayList;
import java.util.List;

/** Renders posting detail and posting-page payloads for text and CSV output modes. */
final class CliPostingOutputRenderer {
  private CliPostingOutputRenderer() {}

  static String renderPostingText(BookIdentity bookIdentity, PostingFact postingFact) {
    List<List<String>> header = new ArrayList<>(CliBookIdentityDisplay.summaryRows(bookIdentity));
    header.add(List.of("Posting id", postingFact.postingId().value()));
    header.add(
        List.of("Posting kind", CliPostingLabels.displayPostingKind(postingFact.postingKind())));
    header.add(List.of("Posting role", CliPostingLabels.displayPostingRoleText(postingFact)));
    header.add(List.of("Effective date", postingFact.journalEntry().effectiveDate().toString()));
    header.add(
        List.of("Recorded at", CliTextDisplay.instant(postingFact.provenance().recordedAt())));
    header.add(List.of("Actor id", postingFact.provenance().requestProvenance().actorId().value()));
    header.add(
        List.of(
            "Actor type",
            displayWireLabel(
                postingFact.provenance().requestProvenance().actorType().wireValue())));
    header.add(
        List.of("Command id", postingFact.provenance().requestProvenance().commandId().value()));
    header.add(
        List.of(
            "Idempotency key",
            postingFact.provenance().requestProvenance().idempotencyKey().value()));
    header.add(
        List.of(
            "Causation id", postingFact.provenance().requestProvenance().causationId().value()));
    header.add(
        List.of(
            "Correlation id",
            postingFact
                .provenance()
                .requestProvenance()
                .correlationId()
                .map(dev.erst.fingrind.core.CorrelationId::value)
                .orElse("(none)")));
    header.add(
        List.of(
            "Source channel",
            displayWireLabel(postingFact.provenance().sourceChannel().wireValue())));
    header.add(
        List.of(
            "Source documents", CliPostingFactFormatter.postingSourceDocumentsText(postingFact)));
    header.add(List.of("Approvals", CliPostingFactFormatter.postingApprovalsText(postingFact)));
    header.add(List.of("Reverses posting", CliPostingLabels.reversalTargetText(postingFact)));
    header.add(
        List.of(
            "Reversal reason",
            postingFact.reversalReason().map(reason -> reason.value()).orElse("(none)")));
    String journalLines =
        CliTextFormat.renderTable(
            List.of("Account", "Side", "Currency", "Amount"),
            postingFact.journalEntry().lines().stream()
                .map(
                    line ->
                        List.of(
                            line.accountCode().value(),
                            displayWireLabel(line.side().wireValue()),
                            line.amount().currencyUnit().code(),
                            CliTextFormat.displayMoney(line.amount().money())))
                .toList(),
            3);
    return CliTextFormat.renderTitledBlock(
        "Posting",
        CliTextFormat.renderKeyValueBlock(header)
            + System.lineSeparator()
            + System.lineSeparator()
            + journalLines);
  }

  static String renderPostingRegisterText(PostingPage page) {
    String summary =
        CliTextFormat.renderKeyValueBlock(
            page.postings().isEmpty()
                ? List.of(
                    List.of("Outcome", CliQueryScopeText.noMatchesLabel("postings")),
                    List.of("Limit", Integer.toString(page.limit())),
                    List.of(
                        "Next cursor",
                        page.nextCursor().map(cursor -> cursor.wireValue()).orElse("(none)")))
                : List.of(
                    List.of("Returned postings", Integer.toString(page.postings().size())),
                    List.of("Limit", Integer.toString(page.limit())),
                    List.of(
                        "Next cursor",
                        page.nextCursor().map(cursor -> cursor.wireValue()).orElse("(none)"))));
    String postings =
        page.postings().isEmpty()
            ? ""
            : CliTextFormat.renderTable(
                List.of(
                    "Effective date", "Origin", "Role", "Debit", "Credit", "Accounts", "Posting"),
                page.postings().stream()
                    .map(CliPostingFactFormatter::postingRegisterTextRow)
                    .toList(),
                3,
                4);
    String context =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of(
                    "Book",
                    CliBookIdentityDisplay.summaryRows(page.bookIdentity()).getFirst().get(1)),
                List.of(
                    "Account filter",
                    page.accountCodeFilter().map(AccountCode::value).orElse("(all accounts)")),
                List.of(
                    "Effective date range",
                    CliQueryScopeText.dateRange(
                        page.effectiveDateRange().effectiveDateFrom().orElse(null),
                        page.effectiveDateRange().effectiveDateTo().orElse(null)))));
    return CliTextFormat.renderTitledBlock(
        "Postings",
        CliReportRenderSupport.joinSections(
            summary, postings, CliReportRenderSupport.section("Context", context)));
  }

  static String renderPostingRegisterCsv(PostingPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "recordKind",
            "effectiveDate",
            "recordedAt",
            "postingId",
            "postingKind",
            "postingOriginKind",
            "reversalState",
            "reversalTarget",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "accountCode",
            "sourceDocumentId",
            "sourceDocumentType",
            "approvalId",
            "approvalDecision",
            "message"),
        page.postings().isEmpty()
            ? List.of(
                List.of(
                    CliCsvEmptyKinds.SCOPE_EMPTY,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    CliQueryScopeText.noMatchesLabel("postings")))
            : page.postings().stream()
                .flatMap(posting -> postingRegisterCsvRows(posting).stream())
                .toList());
  }

  private static List<List<String>> postingRegisterCsvRows(PostingFact postingFact) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(postingRegisterPostingCsvRow(postingFact));
    postingFact.journalEntry().lines().stream()
        .map(line -> line.accountCode().value())
        .distinct()
        .map(accountCode -> postingRegisterAccountCsvRow(postingFact, accountCode))
        .forEach(rows::add);
    postingFact.evidence().sourceDocuments().stream()
        .map(
            sourceDocument ->
                postingRegisterSourceDocumentCsvRow(
                    postingFact,
                    sourceDocument.sourceDocumentId().value(),
                    sourceDocument.sourceDocumentType().value()))
        .forEach(rows::add);
    postingFact.evidence().approvals().stream()
        .map(
            approval ->
                postingRegisterApprovalCsvRow(
                    postingFact, approval.approvalId().value(), approval.decision().wireValue()))
        .forEach(rows::add);
    return List.copyOf(rows);
  }

  private static List<String> postingRegisterPostingCsvRow(PostingFact postingFact) {
    return List.of(
        "posting",
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        CliPostingLabels.reversalStateWireValue(postingFact),
        CliPostingLabels.reversalTargetCsv(postingFact),
        CliPostingLabels.postingCurrency(postingFact),
        CliPostingLabels.postingDebitTotal(postingFact),
        CliPostingLabels.postingCreditTotal(postingFact),
        "",
        "",
        "",
        "",
        "",
        "");
  }

  private static List<String> postingRegisterAccountCsvRow(
      PostingFact postingFact, String accountCode) {
    return List.of(
        "account",
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        CliPostingLabels.reversalStateWireValue(postingFact),
        CliPostingLabels.reversalTargetCsv(postingFact),
        "",
        "",
        "",
        accountCode,
        "",
        "",
        "",
        "",
        "");
  }

  private static List<String> postingRegisterSourceDocumentCsvRow(
      PostingFact postingFact, String sourceDocumentId, String sourceDocumentType) {
    return List.of(
        "source-document",
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        CliPostingLabels.reversalStateWireValue(postingFact),
        CliPostingLabels.reversalTargetCsv(postingFact),
        "",
        "",
        "",
        "",
        sourceDocumentId,
        sourceDocumentType,
        "",
        "",
        "");
  }

  private static List<String> postingRegisterApprovalCsvRow(
      PostingFact postingFact, String approvalId, String approvalDecision) {
    return List.of(
        "approval",
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        CliPostingLabels.reversalStateWireValue(postingFact),
        CliPostingLabels.reversalTargetCsv(postingFact),
        "",
        "",
        "",
        "",
        "",
        "",
        approvalId,
        approvalDecision,
        "");
  }

  static String displayWireLabel(String wireValue) {
    return switch (wireValue) {
      case "DEBIT" -> "Debit";
      case "CREDIT" -> "Credit";
      case "PERSON" -> "Person";
      case "AGENT" -> "Agent";
      case "SYSTEM" -> "System";
      case "CLI" -> "CLI";
      case "INTERNAL" -> "Internal";
      default -> wireValue.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    };
  }
}
