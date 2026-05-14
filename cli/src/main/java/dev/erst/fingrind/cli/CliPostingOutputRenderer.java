package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import java.util.ArrayList;
import java.util.List;

/** Renders posting detail and posting-page payloads for human and CSV output modes. */
final class CliPostingOutputRenderer {
  private CliPostingOutputRenderer() {}

  static String renderPostingHuman(PostingFact postingFact) {
    List<List<String>> header = new ArrayList<>();
    header.add(List.of("Posting id", postingFact.postingId().value()));
    header.add(
        List.of(
            "Posting kind", CliQueryOutputFormatter.displayPostingKind(postingFact.postingKind())));
    header.add(
        List.of(
            "Reversal state", postingFact.reversalReference().isPresent() ? "Reversal" : "Direct"));
    header.add(List.of("Effective date", postingFact.journalEntry().effectiveDate().toString()));
    header.add(List.of("Recorded at", postingFact.provenance().recordedAt().toString()));
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
                .map(value -> value.value())
                .orElse("(none)")));
    header.add(
        List.of(
            "Source channel",
            displayWireLabel(postingFact.provenance().sourceChannel().wireValue())));
    header.add(
        List.of(
            "Reversal target",
            postingFact
                .reversalReference()
                .map(reference -> reference.priorPostingId().value())
                .orElse("(direct)")));
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
                "Reversal state",
                "Currency",
                "Debit total",
                "Credit total",
                "Accounts",
                "Reversal target"),
            page.postings().stream().map(CliQueryOutputFormatter::postingRegisterHumanRow).toList(),
            5,
            6);
    return CliTextFormat.renderTitledBlock(
        "Postings", summary + System.lineSeparator() + System.lineSeparator() + table);
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
        page.postings().stream().map(CliQueryOutputFormatter::postingRegisterCsvRow).toList());
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
