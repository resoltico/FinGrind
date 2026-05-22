package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingEvidence;
import dev.erst.fingrind.core.ApprovalDecision;
import dev.erst.fingrind.core.ApprovalId;
import dev.erst.fingrind.core.ApprovalReference;
import dev.erst.fingrind.core.ApprovalType;
import dev.erst.fingrind.core.ContentSha256;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceDocumentId;
import dev.erst.fingrind.core.SourceDocumentReference;
import dev.erst.fingrind.core.SourceDocumentType;
import dev.erst.fingrind.core.StorageLocator;
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
            new BookkeepingEntry.ManualAdjustment(
                CliFuzzFixtures.postingKind(command),
                CliFuzzFixtures.journalEntry(command),
                CliFuzzFixtures.postingLineage(command)),
            new AccountingEvidence(
                List.of(
                    new SourceDocumentReference(
                        new SourceDocumentId("document-approval-seed"),
                        new SourceDocumentType("invoice"),
                        LocalDate.parse("2026-04-07"),
                        Instant.parse("2026-04-07T12:00:00Z"),
                        new StorageLocator("s3://evidence/document-approval-seed.pdf"),
                        new ContentSha256(
                            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"))),
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
}
