package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.LedgerExecutionJournal;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerJournalEntry;
import dev.erst.fingrind.contract.LedgerJournalStep;
import dev.erst.fingrind.contract.LedgerPlanResult;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStepFailure;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Unit tests for {@link CliResponseWriter}. */
@NullUnmarked
class CliLedgerPlanResponseWriterTest extends CliResponseWriterTestSupport {

  @Test
  void planRejectionStatus_rejectsSucceededPlansAndMapsFailures() {
    assertEquals(
        ProtocolRejectionStatus.PLAN_REJECTED,
        CliResponseWriter.planRejectionStatus(LedgerPlanStatus.REJECTED));
    assertEquals(
        ProtocolRejectionStatus.PLAN_ASSERTION_FAILED,
        CliResponseWriter.planRejectionStatus(LedgerPlanStatus.ASSERTION_FAILED));
    assertThrows(
        IllegalArgumentException.class,
        () -> CliResponseWriter.planRejectionStatus(LedgerPlanStatus.SUCCEEDED));
  }

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
                LedgerFact.text("accountCode", "1000"),
                LedgerFact.count("bucketCount", 1),
                LedgerFact.group(
                    "balance",
                    List.of(
                        LedgerFact.text("currencyCode", "EUR"),
                        LedgerFact.text("netAmount", "10.00"),
                        LedgerFact.text("balanceSide", "DEBIT")))));

    responseWriter.writeLedgerPlanResult(
        new LedgerPlanResult.Succeeded(
            planId("plan-1"),
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(balanceEntry))));

    JsonNode facts =
        readJson(outputStream).path("payload").path("journal").path("steps").get(0).path("facts");

    assertEquals("text", facts.get(0).path("kind").asText());
    assertEquals("accountCode", facts.get(0).path("name").asText());
    assertEquals("1000", facts.get(0).path("value").asText());
    assertEquals("count", facts.get(1).path("kind").asText());
    assertEquals(1, facts.get(1).path("value").asInt());
    assertEquals("group", facts.get(2).path("kind").asText());
    assertEquals("balance", facts.get(2).path("name").asText());
    assertEquals("currencyCode", facts.get(2).path("facts").get(0).path("name").asText());
    assertEquals("EUR", facts.get(2).path("facts").get(0).path("value").asText());
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
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(rejectedEntry))));

    JsonNode json = readJson(outputStream);

    assertEquals("plan-rejected", json.path("status").asText());
    assertEquals("administration-book-not-initialized", json.path("code").asText());
    assertEquals("rejected", json.path("details").path("plan").path("status").asText());
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
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(assertionFailedEntry))));

    JsonNode json = readJson(outputStream);

    assertEquals("plan-assertion-failed", json.path("status").asText());
    assertEquals("assertion-failed", json.path("code").asText());
    assertEquals("assertion-failed", json.path("details").path("plan").path("status").asText());
  }
}
