package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolPlanKinds;
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
import org.junit.jupiter.api.Test;

/** Unit tests for the canonical ledger-plan contract. */
class LedgerPlanContractTest {
  @Test
  void executionPolicyAndPlan_validateMandatoryShape() {
    LedgerExecutionPolicy policy = policy();
    LedgerStep step = new LedgerStep.OpenBook("open");
    LedgerPlan plan = new LedgerPlan("plan-1", policy, List.of(step));

    assertEquals("plan-1", plan.planId());
    assertEquals(LedgerJournalLevel.NORMAL, policy.journalLevel());
    assertEquals(LedgerFailurePolicy.HALT_ON_FIRST_FAILURE, policy.failurePolicy());
    assertEquals(LedgerTransactionMode.ATOMIC, policy.transactionMode());
    assertThrows(IllegalArgumentException.class, () -> new LedgerPlan("", policy, List.of(step)));
    assertThrows(IllegalArgumentException.class, () -> new LedgerPlan("plan-1", policy, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlan("plan-1", policy, List.of(step, new LedgerStep.OpenBook("open"))));
  }

  @Test
  void stepRecords_publishOperationIdsAndRejectInvalidShape() {
    assertEquals(OperationId.OPEN_BOOK, new LedgerStep.OpenBook("open").operationId());
    assertEquals(
        OperationId.DECLARE_ACCOUNT,
        new LedgerStep.DeclareAccount(
                "declare",
                new DeclareAccountCommand(
                    new AccountCode("1000"),
                    new dev.erst.fingrind.core.AccountName("Cash"),
                    NormalBalance.DEBIT))
            .operationId());
    assertEquals(OperationId.PREFLIGHT_ENTRY, postingStep(true).operationId());
    assertEquals(OperationId.POST_ENTRY, postingStep(false).operationId());
    assertEquals(OperationId.INSPECT_BOOK, new LedgerStep.InspectBook("inspect").operationId());
    assertEquals(
        OperationId.LIST_ACCOUNTS,
        new LedgerStep.ListAccounts("accounts", new ListAccountsQuery(50, 0)).operationId());
    assertEquals(
        OperationId.GET_POSTING,
        new LedgerStep.GetPosting("get", new PostingId("posting-1")).operationId());
    assertEquals(
        OperationId.LIST_POSTINGS,
        new LedgerStep.ListPostings(
                "postings",
                new ListPostingsQuery(Optional.empty(), Optional.empty(), Optional.empty(), 50, 0))
            .operationId());
    assertEquals(
        OperationId.ACCOUNT_BALANCE,
        new LedgerStep.AccountBalance(
                "balance",
                new AccountBalanceQuery(
                    new AccountCode("1000"), Optional.empty(), Optional.empty()))
            .operationId());
    assertEquals(
        OperationId.EXECUTE_PLAN,
        new LedgerStep.Assert(
                "assert", new LedgerAssertion.AccountDeclared(new AccountCode("1000")))
            .operationId());
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
    LedgerFact fact = new LedgerFact("postingId", "posting-1");
    LedgerStepFailure failure = new LedgerStepFailure("rejected", "Rejected.", List.of(fact));
    LedgerJournalEntry success =
        new LedgerJournalEntry(
            "post",
            OperationId.POST_ENTRY,
            LedgerStepStatus.SUCCEEDED,
            startedAt,
            finishedAt,
            List.of(fact),
            Optional.empty());
    LedgerJournalEntry rejected =
        new LedgerJournalEntry(
            "post",
            OperationId.POST_ENTRY,
            LedgerStepStatus.REJECTED,
            startedAt,
            finishedAt,
            List.of(),
            Optional.of(failure));
    LedgerExecutionJournal journal =
        new LedgerExecutionJournal(
            "plan-1", LedgerPlanStatus.SUCCEEDED, startedAt, finishedAt, List.of(success));

    assertEquals(List.of(fact), success.facts());
    assertEquals(failure, rejected.failure().orElseThrow());
    assertEquals(
        "plan-1", new LedgerPlanResult("plan-1", LedgerPlanStatus.SUCCEEDED, journal).planId());
    assertThrows(IllegalArgumentException.class, () -> new LedgerFact("", "value"));
    assertThrows(
        IllegalArgumentException.class, () -> new LedgerStepFailure("", "Rejected.", List.of()));
    assertThrows(
        IllegalArgumentException.class, () -> new LedgerStepFailure("code", "", List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry(
                "post",
                OperationId.POST_ENTRY,
                LedgerStepStatus.SUCCEEDED,
                startedAt,
                finishedAt,
                List.of(),
                Optional.of(failure)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerJournalEntry(
                "post",
                OperationId.POST_ENTRY,
                LedgerStepStatus.REJECTED,
                startedAt,
                finishedAt,
                List.of(),
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LedgerExecutionJournal(
                "plan-1", LedgerPlanStatus.SUCCEEDED, finishedAt, startedAt, List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlanResult("other", LedgerPlanStatus.SUCCEEDED, journal));
    assertThrows(
        IllegalArgumentException.class,
        () -> new LedgerPlanResult("plan-1", LedgerPlanStatus.REJECTED, journal));
  }

  @Test
  void protocolPlanKinds_areCanonical() {
    assertEquals(
        List.of(
            ProtocolPlanKinds.ASSERT_ACCOUNT_DECLARED,
            ProtocolPlanKinds.ASSERT_ACCOUNT_ACTIVE,
            ProtocolPlanKinds.ASSERT_POSTING_EXISTS,
            ProtocolPlanKinds.ASSERT_ACCOUNT_BALANCE),
        ProtocolPlanKinds.all());
    assertTrue(ProtocolPlanKinds.all().contains("assert-account-balance"));
  }

  private static LedgerExecutionPolicy policy() {
    return new LedgerExecutionPolicy(
        LedgerJournalLevel.NORMAL,
        LedgerFailurePolicy.HALT_ON_FIRST_FAILURE,
        LedgerTransactionMode.ATOMIC);
  }

  private static LedgerStep postingStep(boolean preflight) {
    PostEntryCommand command = ContractFixtures.postEntryCommand("idem-1");
    return preflight
        ? new LedgerStep.PreflightEntry("preflight", command)
        : new LedgerStep.PostEntry("post", command);
  }
}
