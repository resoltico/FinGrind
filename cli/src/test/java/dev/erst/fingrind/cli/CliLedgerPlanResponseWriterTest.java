package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliPlanJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.OutputMode;
import dev.erst.fingrind.contract.protocol.PlanResultDetail;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
class CliLedgerPlanResponseWriterTest extends CliResponseWriterTestSupport {
  @Test
  void writeLedgerPlanResult_emitsTypedAndGroupedFacts() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Succeeded balanceEntry =
        new LedgerJournalEntry.Succeeded(
            stepId("balance"),
            LedgerJournalStep.standard(LedgerStepKind.ACCOUNT_BALANCE),
            startedAt,
            finishedAt,
            List.of(
                LedgerFact.group(
                    "account",
                    List.of(
                        LedgerFact.text("accountCode", "1000"),
                        LedgerFact.text("accountName", "Cash"),
                        LedgerFact.text("accountType", "ASSET"),
                        LedgerFact.text("accountNodeKind", "POSTABLE"),
                        LedgerFact.text("normalBalance", "DEBIT"),
                        LedgerFact.flag("active", true),
                        LedgerFact.text("declaredAt", "2026-04-07T10:15:30Z"))),
                LedgerFact.count("bucketCount", 1),
                LedgerFact.group(
                    "balance",
                    List.of(
                        LedgerFact.money("debitTotal", new MonetaryAmount("EUR", "1000")),
                        LedgerFact.money("creditTotal", new MonetaryAmount("EUR", "0")),
                        LedgerFact.money("netAmount", new MonetaryAmount("EUR", "1000")),
                        LedgerFact.text("balanceSide", "DEBIT")))));
    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Succeeded(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(balanceEntry))),
        OutputMode.JSON,
        PlanResultDetail.FULL);
    JsonNode data =
        readJson(outputStream).path("payload").path("journal").path("steps").get(0).path("data");
    assertEquals("1000", data.path("account").path("accountCode").stringValue());
    assertEquals("Cash", data.path("account").path("accountName").stringValue());
    assertEquals(1, data.path("bucketCount").asInt());
    assertEquals(
        "1000", data.path("balances").get(0).path("debitTotal").path("minorUnits").stringValue());
    assertEquals(
        "1000", data.path("balances").get(0).path("netAmount").path("minorUnits").stringValue());
    assertEquals("DEBIT", data.path("balances").get(0).path("balanceSide").stringValue());
  }

  @Test
  void writeLedgerPlanResult_writesRejectedEnvelopeForRejectedPlans() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Rejected rejectedEntry =
        new LedgerJournalEntry.Rejected(
            stepId("declare-cash"),
            LedgerJournalStep.standard(LedgerStepKind.DECLARE_ACCOUNT),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure(
                "administration-book-not-initialized", "Book is not initialized.", List.of()));
    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Rejected(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(rejectedEntry))),
        OutputMode.JSON,
        PlanResultDetail.FULL);
    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("administration-book-not-initialized", json.path("code").stringValue());
    assertEquals("Book is not initialized.", json.path("message").stringValue());
    assertEquals("rejected", json.path("payload").path("status").stringValue());
    assertEquals(
        "declare-cash", json.path("payload").path("summary").path("failedStepId").stringValue());
  }

  @Test
  void writeLedgerPlanResult_writesRejectedEnvelopeForAssertionFailedPlans() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.AssertionFailed assertionFailedEntry =
        new LedgerJournalEntry.AssertionFailed(
            stepId("assert-balance"),
            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure("assertion-failed", "Balance mismatch.", List.of()));
    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.AssertionFailed(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(assertionFailedEntry))),
        OutputMode.JSON,
        PlanResultDetail.FULL);
    JsonNode json = readJson(outputStream);
    assertEquals("error", json.path("status").stringValue());
    assertEquals("assertion-failed", json.path("code").stringValue());
    assertEquals("Balance mismatch.", json.path("message").stringValue());
    assertEquals("assertion-failed", json.path("payload").path("status").stringValue());
    assertEquals(
        "assert-balance", json.path("payload").path("summary").path("failedStepId").stringValue());
  }

  @Test
  void writeLedgerPlanResult_routesRejectedMachineEnvelopeToStdout() throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter =
        new CliResponseWriter(utf8PrintStream(outputStream), utf8PrintStream(diagnosticsStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Rejected rejectedEntry =
        new LedgerJournalEntry.Rejected(
            stepId("declare-cash"),
            LedgerJournalStep.standard(LedgerStepKind.DECLARE_ACCOUNT),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure(
                "administration-book-not-initialized", "Book is not initialized.", List.of()));

    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Rejected(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(rejectedEntry))),
        OutputMode.JSON,
        PlanResultDetail.FULL);

    JsonNode json = readJson(outputStream);
    assertEquals("rejected", json.path("status").stringValue());
    assertEquals("administration-book-not-initialized", json.path("code").stringValue());
    assertEquals("", diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void writeLedgerPlanResult_routesRejectedTextToStdout() {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter =
        new CliResponseWriter(utf8PrintStream(outputStream), utf8PrintStream(diagnosticsStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Rejected rejectedEntry =
        new LedgerJournalEntry.Rejected(
            stepId("declare-cash"),
            LedgerJournalStep.standard(LedgerStepKind.DECLARE_ACCOUNT),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure(
                "administration-book-not-initialized", "Book is not initialized.", List.of()));

    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Rejected(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(rejectedEntry))),
        OutputMode.TEXT,
        PlanResultDetail.FULL);

    String rendered = outputStream.toString(StandardCharsets.UTF_8);
    assertTrue(rendered.contains("rejected"));
    assertTrue(rendered.contains("administration-book-not-initialized"));
    assertEquals("", diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  @Test
  void writeLedgerPlanResult_emitsBoundaryJournalEntriesWithBoundaryCheckpoint()
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CliResponseWriter responseWriter = new CliResponseWriter(utf8PrintStream(outputStream));
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Rejected boundaryEntry =
        new LedgerJournalEntry.Rejected(
            stepId("@plan-boundary:commit"),
            LedgerJournalStep.boundary(LedgerBoundaryCheckpoint.COMMIT),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure(
                LedgerPlanFailure.UNEXPECTED_PLAN_FAILURE.code(), "Commit failed.", List.of()));
    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Rejected(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(boundaryEntry))),
        OutputMode.JSON,
        PlanResultDetail.FULL);
    JsonNode step = readJson(outputStream).path("payload").path("journal").path("steps").get(0);
    assertEquals("plan-boundary", step.path("kind").stringValue());
    assertEquals("commit", step.path("boundaryCheckpoint").stringValue());
    assertTrue(step.path("detailKind").isMissingNode() || step.path("detailKind").isNull());
  }

  @Test
  void ledgerPlanPayload_mapsStandardAssertionAndBoundaryJournalKinds() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Succeeded standardEntry =
        new LedgerJournalEntry.Succeeded(
            stepId("open"),
            LedgerJournalStep.standard(LedgerStepKind.INSPECT_BOOK),
            startedAt,
            finishedAt,
            List.of());
    LedgerJournalEntry.AssertionFailed assertionEntry =
        new LedgerJournalEntry.AssertionFailed(
            stepId("assert-balance"),
            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure("assertion-failed", "Balance mismatch.", List.of()));
    LedgerJournalEntry.Rejected boundaryEntry =
        new LedgerJournalEntry.Rejected(
            stepId("@plan-boundary:commit"),
            LedgerJournalStep.boundary(LedgerBoundaryCheckpoint.COMMIT),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure(
                LedgerPlanFailure.UNEXPECTED_PLAN_FAILURE.code(), "Commit failed.", List.of()));
    CliPlanJsonModels.LedgerPlanPayload rejectedPayload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.Rejected(
                planId("plan-1"),
                new LedgerExecutionJournal(
                    startedAt, finishedAt, List.of(standardEntry, boundaryEntry))),
            PlanResultDetail.FULL);
    CliPlanJsonModels.LedgerExecutionJournalPayload rejectedJournal =
        Objects.requireNonNull(rejectedPayload.journal(), "journal");
    List<CliPlanJsonModels.LedgerJournalEntryPayload> steps = rejectedJournal.steps();
    assertEquals("inspect-book", steps.get(0).kind().wireValue());
    assertNull(steps.get(0).detailKind());
    assertNull(steps.get(0).boundaryCheckpoint());
    assertEquals("plan-boundary", steps.get(1).kind().wireValue());
    assertNull(steps.get(1).detailKind());
    assertEquals(LedgerBoundaryCheckpoint.COMMIT, steps.get(1).boundaryCheckpoint());
    CliPlanJsonModels.LedgerPlanPayload assertionFailedPayload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.AssertionFailed(
                planId("plan-2"),
                new LedgerExecutionJournal(startedAt, finishedAt, List.of(assertionEntry))),
            PlanResultDetail.FULL);
    CliPlanJsonModels.LedgerExecutionJournalPayload assertionJournal =
        Objects.requireNonNull(assertionFailedPayload.journal(), "journal");
    CliPlanJsonModels.LedgerJournalEntryPayload assertionStep = assertionJournal.steps().getFirst();
    assertEquals("assert", assertionStep.kind().wireValue());
    assertEquals(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS, assertionStep.detailKind());
    assertNull(assertionStep.boundaryCheckpoint());
  }

  @Test
  void ledgerPlanPayload_summary_keepsAggregateCountsWithoutJournalDuplication() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerJournalEntry.Succeeded summaryEntry =
        new LedgerJournalEntry.Succeeded(
            stepId("summary"),
            LedgerJournalStep.standard(LedgerStepKind.ACCOUNT_BALANCE),
            startedAt,
            finishedAt,
            List.of(
                LedgerFact.text("accountCode", "1000"),
                LedgerFact.flag("active", true),
                LedgerFact.count("bucketCount", 2),
                LedgerFact.money("netAmount", new MonetaryAmount("EUR", "1000")),
                LedgerFact.group(
                    "balance",
                    List.of(
                        LedgerFact.money("netAmount", new MonetaryAmount("EUR", "1000")),
                        LedgerFact.text("balanceSide", "DEBIT")))));

    CliPlanJsonModels.LedgerPlanPayload payload =
        CliPlanPayloadMapper.ledgerPlanPayload(
            new LedgerPlanResult.Succeeded(
                planId("plan-1"),
                new LedgerExecutionJournal(startedAt, finishedAt, List.of(summaryEntry))),
            PlanResultDetail.SUMMARY);

    assertEquals(1, payload.summary().stepCount());
    assertEquals(1, payload.summary().succeededStepCount());
    assertEquals(0, payload.summary().failedStepCount());
    assertNull(payload.summary().failedStepId());
    assertNull(payload.journal());
  }
}
