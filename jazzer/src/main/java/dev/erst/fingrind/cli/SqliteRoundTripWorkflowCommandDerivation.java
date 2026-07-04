package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Derives deterministic workflow variants for SQLite round-trip coverage. */
final class SqliteRoundTripWorkflowCommandDerivation {
  private SqliteRoundTripWorkflowCommandDerivation() {}

  static PostEntryCommand syntheticDirectCommand(PostEntryCommand command, String scenario) {
    String stableToken = derivedStableToken(command.requestProvenance(), scenario);
    return new PostEntryCommand(
        directAdministrativeEntry(command),
        derivedEvidence(command.evidence(), stableToken),
        buildDerivedRequestProvenance(command.requestProvenance(), stableToken),
        command.sourceChannel());
  }

  static PostEntryCommand derivedExactReversalCommand(
      PostEntryCommand command, PostingId targetPostingId, String scenario) {
    String stableToken = derivedStableToken(command.requestProvenance(), scenario);
    java.time.LocalDate reversalEffectiveDate = reversalEffectiveDate(command);
    JournalEntry derivedJournalEntry =
        new JournalEntry(
            reversalEffectiveDate,
            exactReversalLines(CliFuzzFixtures.journalEntry(command).lines()));
    return new PostEntryCommand(
        new BookkeepingEntry.Reversal(
            reversalEffectiveDate,
            new PostingLineage.Reversal(
                new ReversalReference(targetPostingId), new ReversalReason("Derived " + scenario)),
            command.entry().foreignExchangeDetails(),
            derivedJournalEntry),
        derivedEvidence(command.evidence(), stableToken),
        buildDerivedRequestProvenance(command.requestProvenance(), stableToken),
        command.sourceChannel());
  }

  static PostEntryCommand derivedNearMissReversalCommand(
      PostEntryCommand command, PostingId targetPostingId, String scenario) {
    String stableToken = derivedStableToken(command.requestProvenance(), scenario);
    java.time.LocalDate reversalEffectiveDate = reversalEffectiveDate(command);
    JournalEntry derivedJournalEntry =
        new JournalEntry(
            reversalEffectiveDate,
            nonNegatingReversalLines(CliFuzzFixtures.journalEntry(command).lines()));
    return new PostEntryCommand(
        new BookkeepingEntry.Reversal(
            reversalEffectiveDate,
            new PostingLineage.Reversal(
                new ReversalReference(targetPostingId), new ReversalReason("Derived " + scenario)),
            command.entry().foreignExchangeDetails(),
            derivedJournalEntry),
        derivedEvidence(command.evidence(), stableToken),
        buildDerivedRequestProvenance(command.requestProvenance(), stableToken),
        command.sourceChannel());
  }

  private static BookkeepingEntry directAdministrativeEntry(PostEntryCommand command) {
    if (command.entry() instanceof BookkeepingEntry.OpeningPosition openingPosition) {
      return new BookkeepingEntry.OpeningPosition(
          openingPosition.effectiveDate(), openingPosition.balances());
    }
    JournalEntry journalEntry = CliFuzzFixtures.journalEntry(command);
    return new BookkeepingEntry.OpeningPosition(
        journalEntry.effectiveDate(),
        journalEntry.lines().stream()
            .map(
                line ->
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        line.accountCode(),
                        line.side(),
                        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                            line.amount().money())))
            .toList());
  }

  static List<JournalLine> exactReversalLines(List<JournalLine> lines) {
    return lines.stream()
        .map(
            line ->
                new JournalLine(
                    line.accountCode(), oppositeSide(line.side()), line.amount().money()))
        .toList();
  }

  static List<JournalLine> nonNegatingReversalLines(List<JournalLine> lines) {
    List<JournalLine> reversed = new java.util.ArrayList<>(exactReversalLines(lines));
    int firstIndex = 0;
    JournalLine.EntrySide firstSide = reversed.getFirst().side();
    int secondIndex = -1;
    for (int index = 1; index < reversed.size(); index++) {
      if (reversed.get(index).side() != firstSide) {
        secondIndex = index;
        break;
      }
    }
    if (secondIndex < 0) {
      throw new IllegalStateException(
          "Derived near-miss reversal requires at least one line on each side.");
    }
    reversed.set(firstIndex, incrementLineAmount(reversed.get(firstIndex)));
    reversed.set(secondIndex, incrementLineAmount(reversed.get(secondIndex)));
    return List.copyOf(reversed);
  }

  static RequestProvenance derivedRequestProvenance(RequestProvenance provenance, String scenario) {
    return buildDerivedRequestProvenance(provenance, derivedStableToken(provenance, scenario));
  }

  private static java.time.LocalDate reversalEffectiveDate(PostEntryCommand command) {
    return CliFuzzFixtures.journalEntry(command).effectiveDate();
  }

  private static String derivedStableToken(RequestProvenance provenance, String scenario) {
    return UUID.nameUUIDFromBytes(
            (provenance.commandId().value()
                    + "|"
                    + provenance.idempotencyKey().value()
                    + "|"
                    + scenario)
                .getBytes(StandardCharsets.UTF_8))
        .toString();
  }

  private static RequestProvenance buildDerivedRequestProvenance(
      RequestProvenance provenance, String stableToken) {
    return new RequestProvenance(
        provenance.actorId(),
        provenance.actorType(),
        new CommandId("command-" + stableToken),
        new IdempotencyKey(stableToken),
        new CausationId("cause-" + stableToken),
        provenance.correlationId().map(ignored -> new CorrelationId("corr-" + stableToken)));
  }

  private static AccountingEvidence derivedEvidence(
      AccountingEvidence evidence, String stableToken) {
    return new AccountingEvidence(
        evidence.sourceDocuments().stream()
            .map(
                sourceDocument ->
                    new SourceDocumentReference(
                        new SourceDocumentId(
                            sourceDocument.sourceDocumentId().value() + "-" + stableToken),
                        new SourceDocumentType(sourceDocument.sourceDocumentType().value()),
                        sourceDocument.documentDate()))
            .toList(),
        evidence.approvals().stream()
            .map(
                approval ->
                    new ApprovalReference(
                        new ApprovalId(approval.approvalId().value() + "-" + stableToken),
                        new ApprovalType(approval.approvalType().value()),
                        new ActorId(approval.approverId().value() + "-" + stableToken),
                        ActorType.AGENT,
                        ApprovalDecision.APPROVED,
                        approval.approvedAt()))
            .toList());
  }

  private static JournalLine incrementLineAmount(JournalLine line) {
    return new JournalLine(
        line.accountCode(),
        line.side(),
        Money.ofMinorUnits(
            line.amount().currencyUnit(), Math.addExact(line.amount().minorUnits(), 1L)));
  }

  private static JournalLine.EntrySide oppositeSide(JournalLine.EntrySide side) {
    return switch (side) {
      case DEBIT -> JournalLine.EntrySide.CREDIT;
      case CREDIT -> JournalLine.EntrySide.DEBIT;
    };
  }
}
