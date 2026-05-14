package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Derives deterministic workflow variants for SQLite round-trip coverage. */
final class SqliteRoundTripWorkflowCommandDerivation {
  private SqliteRoundTripWorkflowCommandDerivation() {}

  static PostEntryCommand syntheticDirectCommand(PostEntryCommand command, String scenario) {
    return new PostEntryCommand(
        PostingKind.STANDARD,
        command.journalEntry(),
        PostingLineage.direct(),
        derivedRequestProvenance(command.requestProvenance(), scenario),
        command.sourceChannel());
  }

  static PostEntryCommand derivedExactReversalCommand(
      PostEntryCommand command, PostingId targetPostingId, String scenario) {
    return new PostEntryCommand(
        command.postingKind(),
        new JournalEntry(
            command.journalEntry().effectiveDate().plusDays(1),
            exactReversalLines(command.journalEntry().lines())),
        PostingLineage.reversal(
            new ReversalReference(targetPostingId), new ReversalReason("Derived " + scenario)),
        derivedRequestProvenance(command.requestProvenance(), scenario),
        command.sourceChannel());
  }

  static PostEntryCommand derivedNearMissReversalCommand(
      PostEntryCommand command, PostingId targetPostingId, String scenario) {
    return new PostEntryCommand(
        command.postingKind(),
        new JournalEntry(
            command.journalEntry().effectiveDate().plusDays(1),
            nonNegatingReversalLines(command.journalEntry().lines())),
        PostingLineage.reversal(
            new ReversalReference(targetPostingId), new ReversalReason("Derived " + scenario)),
        derivedRequestProvenance(command.requestProvenance(), scenario),
        command.sourceChannel());
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
    String stableToken =
        UUID.nameUUIDFromBytes(
                (provenance.commandId().value()
                        + "|"
                        + provenance.idempotencyKey().value()
                        + "|"
                        + scenario)
                    .getBytes(StandardCharsets.UTF_8))
            .toString();
    return new RequestProvenance(
        provenance.actorId(),
        provenance.actorType(),
        new CommandId("command-" + stableToken),
        new IdempotencyKey(stableToken),
        new CausationId("cause-" + stableToken),
        provenance.correlationId().map(ignored -> new CorrelationId("corr-" + stableToken)));
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
