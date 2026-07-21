package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import java.util.ArrayList;
import java.util.List;

/** Renders posting detail and posting-page payloads for text and CSV output modes. */
final class CliPostingOutputRenderer {
  private static final String RECORD_KIND = CliCsvExportFamilies.POSTINGS;

  private CliPostingOutputRenderer() {}

  static String renderPostingText(
      BookIdentity bookIdentity, PostingFact postingFact, boolean withContext) {
    List<List<String>> header = new ArrayList<>();
    header.add(List.of("Posting id", postingFact.postingId().value()));
    header.add(
        List.of("Posting kind", CliPostingLabels.displayPostingKind(postingFact.postingKind())));
    header.add(List.of("Posting role", CliPostingLabels.displayPostingRoleText(postingFact)));
    header.add(List.of("Effective date", postingFact.journalEntry().effectiveDate().toString()));
    header.add(
        List.of("Recorded at", CliTextDisplay.instant(postingFact.provenance().recordedAt())));
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
        CliJournalLineTextRenderer.renderLines(postingFact.journalEntry().lines());
    List<String> sections = new ArrayList<>();
    sections.add(CliTextFormat.renderKeyValueBlock(header));
    postingFact
        .callerAuthoredEntry()
        .map(CliPostingEntryPayloadSupport::entryPayload)
        .ifPresent(
            entry ->
                sections.add(
                    CliReportRenderSupport.section(
                        "Entry facts", CliPostingEntryPayloadSupport.renderEntryFacts(entry))));
    sections.add(CliReportRenderSupport.section("Journal lines", journalLines));
    if (withContext) {
      sections.add(
          CliReportRenderSupport.keyValueSection(
              "Context", CliBookIdentityDisplay.contextRows(bookIdentity)));
    }
    return CliTextFormat.renderTitledBlock(
        "Posting", CliReportRenderSupport.joinSections(sections.toArray(String[]::new)));
  }

  static String renderPostingRegisterText(PostingPage page, boolean withContext) {
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
                    "Effective date",
                    "Origin",
                    "Role",
                    "Debit",
                    "Credit",
                    "Accounts",
                    "Posting ref"),
                page.postings().stream()
                    .map(CliPostingFactFormatter::postingRegisterTextRow)
                    .toList(),
                3,
                4);
    String context =
        CliTextFormat.renderKeyValueBlock(
            mergeContextRows(
                CliBookIdentityDisplay.contextRows(page.bookIdentity()),
                List.of(
                    List.of(
                        "Account filter",
                        page.accountCodeFilter().map(AccountCode::value).orElse("(all accounts)")),
                    List.of(
                        CliTemporalScopeText.summaryLabel(
                            dev.erst.fingrind.contract.protocol.OperationId.LIST_POSTINGS),
                        CliQueryScopeText.dateRange(
                            page.effectiveDateRange().effectiveDateFrom().orElse(null),
                            page.effectiveDateRange().effectiveDateTo().orElse(null))))));
    return CliTextFormat.renderTitledBlock(
        "Postings",
        CliReportRenderSupport.joinSections(
            summary,
            postings,
            withContext ? CliReportRenderSupport.section("Context", context) : ""));
  }

  static String renderPostingRegisterCsv(PostingPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "recordKind",
            "effectiveDate",
            "recordedAt",
            "postingId",
            "postingKind",
            "postingOriginKind",
            "reversalState",
            "reversesPostingId",
            "reversedByPostingId",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "accountCodes",
            "sourceDocumentIds",
            "sourceDocumentTypes",
            "approvalIds",
            "approvalDecisions",
            "message"),
        page.postings().isEmpty()
            ? List.of(
                List.of(
                    CliCsvExportFamilies.POSTINGS,
                    "posting-page:scope-empty",
                    RECORD_KIND,
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
                    "",
                    CliQueryScopeText.noMatchesLabel("postings")))
            : page.postings().stream()
                .map(posting -> postingRegisterCsvRow(page, posting))
                .toList());
  }

  private static List<String> postingRegisterCsvRow(PostingPage page, PostingFact postingFact) {
    return List.of(
        CliCsvExportFamilies.POSTINGS,
        "posting:" + postingFact.postingId().value(),
        RECORD_KIND,
        postingFact.journalEntry().effectiveDate().toString(),
        postingFact.provenance().recordedAt().toString(),
        postingFact.postingId().value(),
        postingFact.postingKind().wireValue(),
        postingFact.postingOriginKind().wireValue(),
        CliPostingLabels.reversalStateWireValue(postingFact),
        CliPostingLabels.reversalTargetCsv(postingFact),
        java.util.Optional.ofNullable(page.reversedByPostingIds().get(postingFact.postingId()))
            .map(dev.erst.fingrind.core.PostingId::value)
            .orElse(""),
        CliPostingLabels.postingCurrency(postingFact),
        CliPostingLabels.postingDebitTotal(postingFact),
        CliPostingLabels.postingCreditTotal(postingFact),
        pipeJoined(
            postingFact.journalEntry().lines().stream()
                .map(line -> line.accountCode().value())
                .distinct()
                .toList()),
        pipeJoined(
            postingFact.evidence().sourceDocuments().stream()
                .map(sourceDocument -> sourceDocument.sourceDocumentId().value())
                .toList()),
        pipeJoined(
            postingFact.evidence().sourceDocuments().stream()
                .map(sourceDocument -> sourceDocument.sourceDocumentType().value())
                .toList()),
        pipeJoined(
            postingFact.evidence().approvals().stream()
                .map(approval -> approval.approvalId().value())
                .toList()),
        pipeJoined(
            postingFact.evidence().approvals().stream()
                .map(approval -> approval.decision().wireValue())
                .toList()),
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

  private static List<List<String>> mergeContextRows(
      List<List<String>> firstRows, List<List<String>> secondRows) {
    List<List<String>> rows = new ArrayList<>(firstRows);
    rows.addAll(secondRows);
    return List.copyOf(rows);
  }

  private static String pipeJoined(List<String> values) {
    return values.stream().collect(java.util.stream.Collectors.joining("|"));
  }
}
