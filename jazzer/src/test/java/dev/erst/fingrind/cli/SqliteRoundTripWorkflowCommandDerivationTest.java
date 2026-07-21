package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SqliteRoundTripWorkflowCommandDerivationTest {
  @Test
  void reversal_and_provenance_helpers_cover_edge_paths() {
    var command = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    assertEquals(
        2,
        SqliteRoundTripWorkflowCommandDerivation.nonNegatingReversalLines(
                CliFuzzFixtures.journalEntry(command).lines())
            .size());
    assertEquals(
        3,
        SqliteRoundTripWorkflowCommandDerivation.nonNegatingReversalLines(
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new AccountCode("1100"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "1.00")),
                    new JournalLine(
                        new AccountCode("2000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "11.00"))))
            .size());

    IllegalStateException oneSided =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteRoundTripWorkflowCommandDerivation.nonNegatingReversalLines(
                    List.of(
                        new JournalLine(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00")))));
    SqliteRoundTripWorkflowTestSupport.assertMessageContains(
        oneSided, "at least one line on each side");

    RequestProvenance withCorrelation =
        SqliteRoundTripWorkflowCommandDerivation.derivedRequestProvenance(
            command.requestProvenance(), "derived");
    assertTrue(withCorrelation.correlationId().isPresent());

    RequestProvenance withoutCorrelation =
        SqliteRoundTripWorkflowCommandDerivation.derivedRequestProvenance(
            new RequestProvenance(
                command.requestProvenance().actorId(),
                command.requestProvenance().actorType(),
                command.requestProvenance().commandId(),
                command.requestProvenance().idempotencyKey(),
                command.requestProvenance().causationId(),
                Optional.empty()),
            "derived-no-correlation");
    assertTrue(withoutCorrelation.correlationId().isEmpty());

    PostEntryCommand approvalBearingCommand =
        new PostEntryCommand(
            new BookkeepingEntry.OpeningPosition(
                CliFuzzFixtures.journalEntry(command).effectiveDate(),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("EUR", "1000"),
                        null),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("EUR", "1000"),
                        null))),
            new AccountingEvidence(
                List.of(
                    new SourceDocumentReference(
                        new SourceDocumentId("document-approval-seed"),
                        new SourceDocumentType("cash-receipt"),
                        LocalDate.parse("2026-04-07"))),
                List.of(
                    new ApprovalReference(
                        new ApprovalId("approval-seed"),
                        new ApprovalType("manager-signoff"),
                        command.requestProvenance().actorId(),
                        command.requestProvenance().actorType(),
                        ApprovalDecision.APPROVED,
                        Instant.parse("2026-04-07T13:00:00Z")))),
            command.requestProvenance(),
            command.sourceChannel());
    PostEntryCommand derivedCommand =
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
            approvalBearingCommand, "approval-derivation");
    assertEquals(1, derivedCommand.evidence().approvals().size());
    assertTrue(
        derivedCommand
            .evidence()
            .sourceDocuments()
            .getFirst()
            .sourceDocumentId()
            .value()
            .startsWith("document-approval-seed-"));
    assertTrue(
        derivedCommand
            .evidence()
            .approvals()
            .getFirst()
            .approvalId()
            .value()
            .startsWith("approval-seed-"));
  }

  @Test
  void syntheticDirectCommand_preserves_opening_position_shape_and_rewrites_non_opening_entries() {
    PostEntryCommand baseCommand = SqliteRoundTripWorkflowTestSupport.basicValidCommand();
    PostEntryCommand derivedDirectCommand =
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
            baseCommand, "typed-to-opening-position");
    assertTrue(derivedDirectCommand.entry() instanceof BookkeepingEntry.OpeningPosition);

    PostEntryCommand openingPositionCommand =
        new PostEntryCommand(
            new BookkeepingEntry.OpeningPosition(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("EUR", "1000"),
                        null),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("EUR", "1000"),
                        null))),
            baseCommand.evidence(),
            baseCommand.requestProvenance(),
            baseCommand.sourceChannel());

    PostEntryCommand derivedOpeningPositionCommand =
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
            openingPositionCommand, "opening-position-direct");
    assertTrue(derivedOpeningPositionCommand.entry() instanceof BookkeepingEntry.OpeningPosition);

    PostEntryCommand cashExpenseCommand =
        withEntry(
            baseCommand,
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-08"),
                new AccountCode("6100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "2500"),
                null,
                null,
                null));
    assertTrue(
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                    cashExpenseCommand, "cash-expense-direct")
                .entry()
            instanceof BookkeepingEntry.OpeningPosition);

    PostEntryCommand equityContributionCommand =
        withEntry(
            baseCommand,
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-08"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "2500"),
                null));
    assertTrue(
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                    equityContributionCommand, "equity-contribution-direct")
                .entry()
            instanceof BookkeepingEntry.OpeningPosition);

    PostEntryCommand equityWithdrawalCommand =
        withEntry(
            baseCommand,
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-08"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "2500"),
                null));
    assertTrue(
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                    equityWithdrawalCommand, "equity-withdrawal-direct")
                .entry()
            instanceof BookkeepingEntry.OpeningPosition);

    PostEntryCommand reversalCommand =
        withEntry(
            baseCommand,
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-08"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new dev.erst.fingrind.core.ReversalReference(
                        new dev.erst.fingrind.core.PostingId("41a95cd2-4a5f-3ef3-8a33-c2771905f362")),
                    new dev.erst.fingrind.core.ReversalReason("reverse direct derivation target")),
                null,
                new JournalEntry(
                    LocalDate.parse("2026-04-08"),
                    List.of(
                        new JournalLine(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "10.00")),
                        new JournalLine(
                            new AccountCode("2000"),
                            JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "10.00"))))));
    assertTrue(
        SqliteRoundTripWorkflowCommandDerivation.syntheticDirectCommand(
                    reversalCommand, "reversal-direct")
                .entry()
            instanceof BookkeepingEntry.OpeningPosition);
  }

  @Test
  void derivedReversalCommands_keep_the_already_admitted_effective_date() {
    PostEntryCommand baseCommand = SqliteRoundTripWorkflowTestSupport.basicValidCommand();

    PostEntryCommand exactReversal =
        SqliteRoundTripWorkflowCommandDerivation.derivedExactReversalCommand(
            baseCommand, new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"), "exact-reversal-date");
    PostEntryCommand nearMissReversal =
        SqliteRoundTripWorkflowCommandDerivation.derivedNearMissReversalCommand(
            baseCommand, new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"), "near-miss-reversal-date");

    assertEquals(
        CliFuzzFixtures.journalEntry(baseCommand).effectiveDate(),
        CliFuzzFixtures.journalEntry(exactReversal).effectiveDate());
    assertEquals(
        CliFuzzFixtures.journalEntry(baseCommand).effectiveDate(),
        CliFuzzFixtures.journalEntry(nearMissReversal).effectiveDate());
  }

  private static PostEntryCommand withEntry(PostEntryCommand template, BookkeepingEntry entry) {
    return new PostEntryCommand(
        entry, template.evidence(), template.requestProvenance(), template.sourceChannel());
  }
}
