package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import java.util.ArrayList;
import java.util.List;

/** Renders posting detail and posting-page payloads for human and CSV output modes. */
final class CliPostingOutputRenderer {
  private CliPostingOutputRenderer() {}

  static String renderPostingHuman(BookIdentity bookIdentity, PostingFact postingFact) {
    List<List<String>> header = new ArrayList<>(CliBookIdentityDisplay.rows(bookIdentity));
    header.add(List.of("Posting id", postingFact.postingId().value()));
    header.add(
        List.of(
            "Posting kind", CliQueryOutputFormatter.displayPostingKind(postingFact.postingKind())));
    header.add(
        List.of("Posting role", CliQueryOutputFormatter.displayPostingRoleHuman(postingFact)));
    header.add(List.of("Effective date", postingFact.journalEntry().effectiveDate().toString()));
    header.add(
        List.of("Recorded at", CliHumanDisplay.instant(postingFact.provenance().recordedAt())));
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
        List.of("Reverses posting", CliQueryOutputFormatter.reversalTargetHuman(postingFact)));
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

  static String renderPostingRegisterHuman(PostingPage page) {
    List<List<String>> headerRows =
        new ArrayList<>(CliBookIdentityDisplay.rows(page.bookIdentity()));
    headerRows.add(
        List.of(
            "Account filter",
            page.accountCodeFilter().map(AccountCode::value).orElse("(all accounts)")));
    headerRows.add(
        List.of(
            "Effective date range",
            CliQueryOutputFormatter.dateRange(
                page.effectiveDateRange().effectiveDateFrom().orElse(null),
                page.effectiveDateRange().effectiveDateTo().orElse(null))));
    String header = CliTextFormat.renderKeyValueBlock(List.copyOf(headerRows));
    String summary =
        CliTextFormat.renderKeyValueBlock(
            List.of(
                List.of("Returned postings", Integer.toString(page.postings().size())),
                List.of("Limit", Integer.toString(page.limit())),
                List.of(
                    "Next cursor",
                    page.nextCursor().map(cursor -> cursor.wireValue()).orElse("(none)"))));
    String table =
        CliTextFormat.renderTable(
            List.of(
                "Effective date",
                "Recorded at",
                "Posting id",
                "Posting kind",
                "Posting role",
                "Currency",
                "Debit total",
                "Credit total",
                "Accounts",
                "Reverses posting"),
            page.postings().stream().map(CliQueryOutputFormatter::postingRegisterHumanRow).toList(),
            5,
            6);
    return CliTextFormat.renderTitledBlock(
        "Postings",
        header
            + System.lineSeparator()
            + System.lineSeparator()
            + summary
            + System.lineSeparator()
            + System.lineSeparator()
            + table);
  }

  static String renderPostingRegisterCsv(PostingPage page) {
    return CliTextFormat.renderCsv(
        List.of(
            "effectiveDate",
            "recordedAt",
            "postingId",
            "postingKind",
            "reversalState",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "accountCodes",
            "reversalTarget"),
        page.postings().stream()
            .map(
                posting -> {
                  List<String> row = new ArrayList<>();
                  row.addAll(CliQueryOutputFormatter.postingRegisterCsvRow(posting));
                  return List.copyOf(row);
                })
            .toList());
  }

  static String displayWireLabel(String wireValue) {
    return switch (wireValue) {
      case "DEBIT" -> "Debit";
      case "CREDIT" -> "Credit";
      case "HUMAN" -> "Human";
      case "AGENT" -> "Agent";
      case "SYSTEM" -> "System";
      case "CLI" -> "CLI";
      case "INTERNAL" -> "Internal";
      default -> wireValue.replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
    };
  }
}
