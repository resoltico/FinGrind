package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.ProtocolLimits;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Unit tests for the canonical ledger-plan contract. */
class LedgerPlanContractTest {
  @Test
  void ledgerPlan_validatesMandatoryShape() {
    LedgerStep step = new LedgerStep.OpenBook("open");
    LedgerPlan plan = new LedgerPlan("plan-1", List.of(step));

    assertEquals("plan-1", plan.planId());
    assertTrue(plan.beginsWithOpenBook());
    assertThrows(IllegalArgumentException.class, () -> new LedgerPlan("", List.of(step)));
    assertThrows(IllegalArgumentException.class, () -> new LedgerPlan("plan-1", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerPlan(
                "plan-1",
                List.of(
                    new LedgerStep.InspectBook("duplicate"),
                    new LedgerStep.ListAccounts("duplicate", new ListAccountsQuery(50, 0)))));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerPlan(
                "plan-1",
                List.of(new LedgerStep.InspectBook("inspect"), new LedgerStep.OpenBook("open"))));
  }

  @Test
  void stepRecords_publishCanonicalKindsAndRejectInvalidShape() {
    assertEquals(LedgerStepKind.OPEN_BOOK, new LedgerStep.OpenBook("open").kind());
    assertEquals(
        LedgerStepKind.DECLARE_ACCOUNT,
        new LedgerStep.DeclareAccount(
                "declare",
                new DeclareAccountCommand(
                    new AccountCode("1000"),
                    new dev.erst.fingrind.core.AccountName("Cash"),
                    NormalBalance.DEBIT))
            .kind());
    assertEquals(LedgerStepKind.PREFLIGHT_ENTRY, postingStep(true).kind());
    assertEquals(LedgerStepKind.POST_ENTRY, postingStep(false).kind());
    assertEquals(LedgerStepKind.INSPECT_BOOK, new LedgerStep.InspectBook("inspect").kind());
    assertEquals(
        LedgerStepKind.LIST_ACCOUNTS,
        new LedgerStep.ListAccounts("accounts", new ListAccountsQuery(50, 0)).kind());
    assertEquals(
        LedgerStepKind.GET_POSTING,
        new LedgerStep.GetPosting("get", new PostingId("posting-1")).kind());
    assertEquals(
        LedgerStepKind.LIST_POSTINGS,
        new LedgerStep.ListPostings(
                "postings",
                new ListPostingsQuery(Optional.empty(), Optional.empty(), Optional.empty(), 50, 0))
            .kind());
    assertEquals(
        LedgerStepKind.ACCOUNT_BALANCE,
        new LedgerStep.AccountBalance(
                "balance",
                new AccountBalanceQuery(
                    new AccountCode("1000"), Optional.empty(), Optional.empty()))
            .kind());
    assertEquals(
        LedgerStepKind.ASSERT,
        new LedgerStep.Assert(
                "assert", new LedgerAssertion.AccountDeclared(new AccountCode("1000")))
            .kind());
    assertEquals(
        Optional.of(LedgerAssertionKind.ACCOUNT_DECLARED),
        new LedgerStep.Assert(
                "assert", new LedgerAssertion.AccountDeclared(new AccountCode("1000")))
            .detailKind());
    assertThrows(IllegalArgumentException.class, () -> new LedgerStep.OpenBook(" "));
    assertThrows(NullPointerException.class, () -> new LedgerStep.Assert("assert", null));
  }

  @Test
  void assertions_validateAndExposeBalanceQuery() {
    LedgerAssertion.AccountBalanceEquals assertion =
        new LedgerAssertion.AccountBalanceEquals(
            new AccountCode("1000"),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
            NormalBalance.DEBIT);

    assertEquals(new AccountCode("1000"), assertion.query().accountCode());
    assertEquals(Optional.of(LocalDate.parse("2026-04-01")), assertion.query().effectiveDateFrom());
    assertEquals(
        LedgerAssertionKind.ACCOUNT_ACTIVE,
        new LedgerAssertion.AccountActive(new AccountCode("1000")).kind());
    assertEquals(
        LedgerAssertionKind.POSTING_EXISTS,
        new LedgerAssertion.PostingExists(new PostingId("posting-1")).kind());
    assertEquals(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS, assertion.kind());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerAssertion.AccountBalanceEquals(
                new AccountCode("1000"),
                Optional.of(LocalDate.parse("2026-05-01")),
                Optional.of(LocalDate.parse("2026-04-30")),
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                NormalBalance.DEBIT));
    assertThrows(NullPointerException.class, () -> new LedgerAssertion.AccountDeclared(null));
    assertThrows(NullPointerException.class, () -> new LedgerAssertion.AccountActive(null));
    assertThrows(NullPointerException.class, () -> new LedgerAssertion.PostingExists(null));
  }

  @Test
  void journalAndResult_validateConsistency() {
    Instant startedAt = Instant.parse("2026-04-17T10:15:30Z");
    Instant finishedAt = Instant.parse("2026-04-17T10:15:31Z");
    LedgerFact fact = LedgerFact.text("postingId", "posting-1");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of(fact));
    LedgerJournalEntry success =
        new LedgerJournalEntry.Succeeded(
            "post",
            LedgerStepKind.POST_ENTRY,
            Optional.empty(),
            startedAt,
            finishedAt,
            List.of(fact));
    LedgerJournalEntry rejected =
        new LedgerJournalEntry.Rejected(
            "post",
            LedgerStepKind.ASSERT,
            Optional.of(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS),
            startedAt,
            finishedAt,
            List.of(),
            failure);
    LedgerJournalEntry assertionFailed =
        new LedgerJournalEntry.AssertionFailed(
            "assert",
            LedgerStepKind.ASSERT,
            Optional.of(LedgerAssertionKind.ACCOUNT_DECLARED),
            startedAt,
            finishedAt,
            List.of(),
            new LedgerStepFailure("assertion-failed", "Assertion failed.", List.of()));
    LedgerExecutionJournal journal =
        new LedgerExecutionJournal(
            "plan-1", LedgerPlanStatus.SUCCEEDED, startedAt, finishedAt, List.of(success));
    LedgerExecutionJournal rejectedJournal =
        new LedgerExecutionJournal(
            "plan-1", LedgerPlanStatus.REJECTED, startedAt, finishedAt, List.of(rejected));
    LedgerExecutionJournal assertionFailedJournal =
        new LedgerExecutionJournal(
            "plan-1",
            LedgerPlanStatus.ASSERTION_FAILED,
            startedAt,
            finishedAt,
            List.of(assertionFailed));

    assertEquals(List.of(fact), success.facts());
    assertEquals(failure, ((LedgerJournalEntry.Rejected) rejected).failure());
    assertEquals(LedgerPlanStatus.REJECTED, rejectedJournal.status());
    assertEquals(LedgerPlanStatus.ASSERTION_FAILED, assertionFailedJournal.status());
    assertEquals(Optional.of(LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS), rejected.detailKind());
    assertEquals("plan-1", new LedgerPlanResult.Succeeded("plan-1", journal).planId());
    assertThrows(IllegalArgumentException.class, () -> LedgerFact.text("", "value"));
    assertThrows(NullPointerException.class, () -> LedgerFact.text("count", null));
    assertThrows(
        IllegalArgumentException.class, () -> new LedgerStepFailure("", "Rejected.", List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> new LedgerStepFailure("code", "", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerExecutionJournal(
                "plan-1", LedgerPlanStatus.SUCCEEDED, finishedAt, startedAt, List.of(success)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerExecutionJournal(
                "plan-1", LedgerPlanStatus.REJECTED, startedAt, finishedAt, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerExecutionJournal(
                "plan-1", LedgerPlanStatus.SUCCEEDED, startedAt, finishedAt, List.of(rejected)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerExecutionJournal(
                "plan-1", LedgerPlanStatus.REJECTED, startedAt, finishedAt, List.of(success)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerExecutionJournal(
                "plan-1",
                LedgerPlanStatus.ASSERTION_FAILED,
                startedAt,
                finishedAt,
                List.of(rejected)));
    assertThrows(
        IllegalArgumentException.class, () -> new LedgerPlanResult.Succeeded("other", journal));
    assertThrows(
        IllegalArgumentException.class, () -> new LedgerPlanResult.Rejected("plan-1", journal));
  }

  @Test
  void ledgerPlan_rejectsMoreThanTheProtocolStepLimit() {
    List<LedgerStep> steps =
        IntStream.rangeClosed(0, ProtocolLimits.LEDGER_PLAN_STEP_MAX)
            .mapToObj(index -> (LedgerStep) new LedgerStep.InspectBook("inspect-" + index))
            .toList();

    assertThrows(IllegalArgumentException.class, () -> new LedgerPlan("plan-too-large", steps));
  }

  @Test
  void ledgerPlanKinds_areCanonical() {
    assertEquals(
        List.of(
            LedgerStepKind.OPEN_BOOK.wireValue(),
            LedgerStepKind.DECLARE_ACCOUNT.wireValue(),
            LedgerStepKind.PREFLIGHT_ENTRY.wireValue(),
            LedgerStepKind.POST_ENTRY.wireValue(),
            LedgerStepKind.INSPECT_BOOK.wireValue(),
            LedgerStepKind.LIST_ACCOUNTS.wireValue(),
            LedgerStepKind.GET_POSTING.wireValue(),
            LedgerStepKind.LIST_POSTINGS.wireValue(),
            LedgerStepKind.ACCOUNT_BALANCE.wireValue(),
            LedgerStepKind.ASSERT.wireValue()),
        LedgerStepKind.wireValues());
    assertEquals(
        List.of(
            LedgerAssertionKind.ACCOUNT_DECLARED.wireValue(),
            LedgerAssertionKind.ACCOUNT_ACTIVE.wireValue(),
            LedgerAssertionKind.POSTING_EXISTS.wireValue(),
            LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS.wireValue()),
        LedgerAssertionKind.wireValues());
    assertTrue(LedgerAssertionKind.wireValues().contains("assert-account-balance"));
    assertEquals(
        List.of("succeeded", "rejected", "assertion-failed"), LedgerPlanStatus.wireValues());
    assertEquals(LedgerPlanStatus.SUCCEEDED, LedgerPlanStatus.fromWireValue("succeeded"));
    assertEquals(LedgerPlanStatus.REJECTED, LedgerPlanStatus.fromWireValue("rejected"));
    assertEquals(
        LedgerPlanStatus.ASSERTION_FAILED, LedgerPlanStatus.fromWireValue("assertion-failed"));
    assertThrows(IllegalArgumentException.class, () -> LedgerPlanStatus.fromWireValue("FAILED"));
    assertEquals(
        List.of("succeeded", "rejected", "assertion-failed"), LedgerStepStatus.wireValues());
    assertEquals(LedgerStepStatus.SUCCEEDED, LedgerStepStatus.fromWireValue("succeeded"));
    assertEquals(LedgerStepStatus.REJECTED, LedgerStepStatus.fromWireValue("rejected"));
    assertEquals(
        LedgerStepStatus.ASSERTION_FAILED, LedgerStepStatus.fromWireValue("assertion-failed"));
    assertThrows(IllegalArgumentException.class, () -> LedgerStepStatus.fromWireValue("FAILED"));
  }

  private static LedgerStep postingStep(boolean preflight) {
    PostEntryCommand command = ContractFixtures.postEntryCommand("idem-1");
    return preflight
        ? new LedgerStep.PreflightEntry("preflight", command)
        : new LedgerStep.PostEntry("post", command);
  }
}
