package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryCheckpoint;
import dev.erst.fingrind.contract.workflow.LedgerExecutionJournal;
import dev.erst.fingrind.contract.workflow.LedgerFact;
import dev.erst.fingrind.contract.workflow.LedgerJournalEntry;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerJournalStep;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for ledger facts, journal entries, and execution journals. */
class LedgerJournalModelTest extends ContractTestSupport {
  @Test
  void ledgerFactsAndJournalValidationCoverTypedAndFailureBranches() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of());
    LedgerJournalStep standardStep = LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY);
    LedgerJournalStep assertionStep =
        LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_DECLARED);
    LedgerJournalStep boundaryStep = LedgerJournalStep.boundary(LedgerBoundaryCheckpoint.COMMIT);
    MonetaryAmount amount = monetaryAmount("EUR", "10.00");
    assertEquals("value", LedgerFact.text("text", "value").value());
    assertTrue(LedgerFact.flag("flag", true).value());
    assertEquals(7, LedgerFact.count("count", 7).value());
    assertEquals(amount, LedgerFact.money("amount", amount).value());
    assertEquals(
        List.of(LedgerFact.text("currencyCode", "EUR")),
        LedgerFact.group("balance", List.of(LedgerFact.text("currencyCode", "EUR"))).facts());
    assertEquals(LedgerStepKind.POST_ENTRY, standardStep.kind());
    assertNull(standardStep.detailKind());
    assertEquals(LedgerStepKind.ASSERT, assertionStep.kind());
    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, assertionStep.detailKind());
    assertEquals(LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, boundaryStep.kind());
    assertNull(boundaryStep.detailKind());
    assertEquals(LedgerBoundaryCheckpoint.COMMIT, boundaryStep.boundaryCheckpoint());
    assertThrows(NullPointerException.class, () -> new LedgerFact.Text("null", nullOf()));
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.count(" ", 7));
    assertThrows(NullPointerException.class, () -> LedgerFact.money("amount", nullOf()));
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.group("balance", List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> LedgerJournalStep.standard(LedgerStepKind.ASSERT));
    assertThrows(NullPointerException.class, () -> LedgerJournalStep.assertion(nullOf()));
    assertThrows(NullPointerException.class, () -> LedgerJournalStep.boundary(nullOf()));
    LedgerJournalEntry detailed =
        new LedgerJournalEntry.Rejected(
            stepId("assert"),
            assertionStep,
            startedAt,
            finishedAt,
            List.of(LedgerFact.flag("active", false)),
            failure);
    LedgerJournalEntry nullableOptionals =
        new LedgerJournalEntry.Succeeded(
            stepId("post"), standardStep, startedAt, finishedAt, List.of());
    LedgerJournalEntry assertionFailed =
        new LedgerJournalEntry.AssertionFailed(
            stepId("assert-balance"),
            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
            startedAt,
            finishedAt,
            List.of(LedgerFact.text("currencyCode", "EUR")),
            failure);
    LedgerJournalEntry boundaryFailed =
        new LedgerJournalEntry.Rejected(
            stepId("@plan-boundary:commit"),
            boundaryStep,
            startedAt,
            finishedAt,
            List.of(),
            failure);
    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, detailed.detailKind());
    assertEquals(LedgerStepKind.ASSERT, detailed.kind());
    assertNull(nullableOptionals.detailKind());
    assertEquals(LedgerStepKind.POST_ENTRY, nullableOptionals.kind());
    assertNull(nullableOptionals.boundaryCheckpoint());
    assertEquals(LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY, boundaryFailed.kind());
    assertEquals(LedgerBoundaryCheckpoint.COMMIT, boundaryFailed.boundaryCheckpoint());
    assertNull(boundaryFailed.detailKind());
    assertNull(new LedgerStep.EnsureBook(stepId("open"), openBookCommand()).detailKind());
    assertEquals(Optional.of(failure), detailed.optionalFailure());
    assertEquals(Optional.empty(), nullableOptionals.optionalFailure());
    assertEquals(Optional.of(failure), assertionFailed.optionalFailure());
    assertEquals(Optional.of(failure), boundaryFailed.optionalFailure());
    assertEquals(LedgerStepStatus.REJECTED, detailed.status());
    assertEquals(LedgerStepStatus.SUCCEEDED, nullableOptionals.status());
    assertEquals(LedgerStepStatus.ASSERTION_FAILED, assertionFailed.status());
    assertThrows(IllegalStateException.class, nullableOptionals::requiredFailure);
    assertEquals(failure, ((LedgerJournalEntry.Failed) detailed).requiredFailure());
    assertEquals(failure, assertionFailed.requiredFailure());
    assertThrows(
        NullPointerException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId("assert"), nullOf(), startedAt, finishedAt, List.of(), failure));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.AssertionFailed(
                stepId("assert"),
                LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                startedAt,
                finishedAt,
                List.of(),
                failure));
    LedgerJournalEntry succeededAssertion =
        new LedgerJournalEntry.Succeeded(
            stepId("post"),
            LedgerJournalStep.assertion(LedgerAssertionKind.ACCOUNT_DECLARED),
            startedAt,
            finishedAt,
            List.of());
    assertEquals(LedgerAssertionKind.ACCOUNT_DECLARED, succeededAssertion.detailKind());
  }

  @Test
  void journalKindsAndBoundaryCheckpointsPublishStableWireValues() {
    assertEquals(
        List.of(
            "declare-account",
            "declare-tax-registration",
            "preflight-entry",
            "record-sale-settled",
            "record-sale-on-credit",
            "record-purchase-settled",
            "record-purchase-on-credit",
            "record-inventory-capitalization-settled",
            "record-inventory-capitalization-on-credit",
            "record-inventory-write-down",
            "record-inventory-shrinkage",
            "record-inventory-count-increase",
            "record-prepayment",
            "record-deferred-revenue",
            "record-accrued-expense",
            "record-accrual-cutoff-recognition",
            "record-accrued-expense-settlement",
            "record-latvian-monthly-payroll",
            "record-latvian-payroll-net-wage-settlement",
            "record-latvian-payroll-state-remittance",
            "record-fixed-asset-capitalization",
            "record-fixed-asset-depreciation",
            "record-fixed-asset-disposal",
            "record-financing-borrowing",
            "record-financing-principal-repayment",
            "record-financing-interest-accrual",
            "record-financing-interest-payment",
            "record-foreign-currency-obligation",
            "record-realized-foreign-exchange-settlement",
            "record-expense-settled",
            "record-expense-on-credit",
            "record-receipt",
            "record-payment",
            "record-owner-contribution",
            "record-owner-withdrawal",
            "record-opening-position",
            "record-reversal",
            "post-entry",
            "inspect-book",
            "list-accounts",
            "get-posting",
            "list-postings",
            "account-balance",
            "assert",
            "plan-boundary"),
        LedgerJournalKind.wireValues());
    assertEquals(
        LedgerJournalKind.BoundaryKind.PLAN_BOUNDARY,
        LedgerJournalKind.fromWireValue("plan-boundary"));
    assertEquals(
        List.of("begin", "initialization-check", "commit", "rollback"),
        LedgerBoundaryCheckpoint.wireValues());
    assertEquals(
        LedgerBoundaryCheckpoint.INITIALIZATION_CHECK,
        LedgerBoundaryCheckpoint.fromWireValue("initialization-check"));
  }

  @Test
  void journalRecordsRejectBlankIdentifiersAndBackwardsTimes() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of());
    LedgerJournalEntry succeededWithoutFailure =
        new LedgerJournalEntry.Succeeded(
            stepId("post"),
            LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
            startedAt,
            finishedAt,
            List.of());
    LedgerExecutionJournal rejectedJournal =
        new LedgerExecutionJournal(
            startedAt,
            finishedAt,
            List.of(
                new LedgerJournalEntry.Rejected(
                    stepId("post"),
                    LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                    startedAt,
                    finishedAt,
                    List.of(),
                    failure)));
    assertTrue(succeededWithoutFailure instanceof LedgerJournalEntry.Succeeded);
    assertEquals(stepId("post"), rejectedJournal.terminalStep().stepId());
    assertEquals(stepId("post"), rejectedJournal.requiredFailedStep().stepId());
    assertThrows(
        IllegalStateException.class,
        () ->
            new LedgerExecutionJournal(startedAt, finishedAt, List.of(succeededWithoutFailure))
                .requiredFailedStep());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId(" "),
                LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                startedAt,
                finishedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry.Rejected(
                stepId("post"),
                LedgerJournalStep.standard(LedgerStepKind.POST_ENTRY),
                finishedAt,
                startedAt,
                List.of(),
                failure));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerExecutionJournal(startedAt, finishedAt, List.of()));
  }
}
