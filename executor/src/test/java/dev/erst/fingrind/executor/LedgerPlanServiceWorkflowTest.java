package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.account;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.groupFact;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.openBookStep;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.postEntryCommand;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.service;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerBoundaryPhase;
import dev.erst.fingrind.contract.workflow.LedgerJournalKind;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepFailure;
import dev.erst.fingrind.contract.workflow.LedgerStepStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering commit, rejection, and rollback workflows in {@link LedgerPlanService}. */
class LedgerPlanServiceWorkflowTest {
  @Test
  void execute_commitsAllSupportedStepFamiliesAndRecordsJournal() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          openBookStep("open"),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)),
                          new LedgerStep.DeclareAccount(
                              stepId("revenue"),
                              account(
                                  "2000", "Revenue", AccountType.REVENUE, NormalBalance.CREDIT)),
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-1")),
                          new LedgerStep.PostEntry(stepId("post"), postEntryCommand("idem-1")),
                          new LedgerStep.InspectBook(stepId("inspect")),
                          new LedgerStep.ListAccounts(
                              stepId("accounts"),
                              new dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery(
                                  50, Optional.empty())),
                          new LedgerStep.GetPosting(stepId("get"), new PostingId("posting-1")),
                          new LedgerStep.ListPostings(
                              stepId("postings"),
                              new dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery(
                                  Optional.empty(), null, null, 50, Optional.empty())),
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              AccountBalanceQuery.unbounded(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-declared"),
                              new LedgerAssertion.AccountDeclared(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-active"),
                              new LedgerAssertion.AccountActive(new AccountCode("1000"))),
                          new LedgerStep.Assert(
                              stepId("assert-posting"),
                              new LedgerAssertion.PostingExists(new PostingId("posting-1"))),
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  null,
                                  null,
                                  Money.parse("EUR", "10.00"),
                                  BalanceSide.DEBIT)))));

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status());
      assertEquals(14, result.journal().steps().size());
      assertTrue(
          result.journal().steps().stream()
              .allMatch(step -> step.status() == LedgerStepStatus.SUCCEEDED));
      assertEquals(LedgerJournalKind.OPEN_BOOK, result.journal().steps().getFirst().kind());
      assertEquals(LedgerJournalKind.ASSERT, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerAssertionKind.ACCOUNT_BALANCE_EQUALS,
          result.journal().steps().getLast().detailKind());
      assertTrue(bookSession.findPosting(new PostingId("posting-1")).isPresent());
    }
  }

  @Test
  void execute_rejectsUninitializedPlanThatDoesNotBeginWithOpenBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "administration-book-not-initialized",
          result.journal().steps().getFirst().requiredFailure().code());
      assertFalse(bookSession.inspectBook().initialized());
    }
  }

  @Test
  void execute_rejectsUninitializedPlansWithFamilySpecificCodes() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var preflightResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-preflight"),
                      List.of(
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-1")))));

      assertEquals(LedgerPlanStatus.REJECTED, preflightResult.status());
      assertEquals(
          "posting-book-not-initialized",
          preflightResult.journal().steps().getFirst().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var queryResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-query"),
                      List.of(
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              AccountBalanceQuery.unbounded(new AccountCode("1000"))))));

      assertEquals(LedgerPlanStatus.REJECTED, queryResult.status());
      assertEquals(
          "query-book-not-initialized",
          queryResult.journal().steps().getFirst().requiredFailure().code());
    }
  }

  @Test
  void execute_rejectsUninitializedAssertionPlansWithQueryFamilyCode() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-query"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-posting"),
                              new LedgerAssertion.PostingExists(new PostingId("posting-1"))))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          result.journal().steps().getFirst().requiredFailure().code());
      assertEquals(
          LedgerAssertionKind.POSTING_EXISTS, result.journal().steps().getFirst().detailKind());
    }
  }

  @Test
  void execute_rollsBackOnPostingRejection() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          openBookStep("open"),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)),
                          new LedgerStep.PostEntry(stepId("post"), postEntryCommand("idem-1")))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      LedgerStepFailure failure = result.journal().steps().getLast().requiredFailure();
      assertEquals(
          PostingRejection.wireCode(
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          failure.code());
      assertTrue(
          failure
              .message()
              .contains("Posting references undeclared, inactive, or non-postable accounts."));
      assertTrue(
          failure.facts().stream()
              .anyMatch(
                  fact ->
                      groupFact(
                          fact, "violation", "code", "unknown-account", "accountCode", "2000")));
      assertFalse(bookSession.inspectBook().initialized());
    }
  }

  @Test
  void execute_rejectsAlreadyInitializedOpenBookAndConflictingRedeclaration() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      var openBookResult =
          service(bookSession)
              .execute(new LedgerPlan(planId("plan-open"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, openBookResult.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.BookAlreadyInitialized()),
          openBookResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountRole(AccountType.ASSET, NormalBalance.DEBIT),
          accountTaxonomy(AccountType.ASSET),
          FIXED_CLOCK.instant());

      var redeclareResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-declare"),
                      List.of(
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.CREDIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, redeclareResult.status());
      assertEquals(
          BookAdministrationRejection.wireCode(
              new BookAdministrationRejection.AccountRoleConflict(
                  new AccountCode("1000"), AccountRole.ORDINARY, AccountRole.CONTRA)),
          redeclareResult.journal().steps().getLast().requiredFailure().code());
    }
  }

  @Test
  void execute_rollsBackAndJournalsUnexpectedRuntimeFailures() {
    try (LedgerPlanServiceTestSupport.ThrowingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.ThrowingLedgerPlanSession()) {
      var service = service(bookSession);

      var result = service.execute(new LedgerPlan(planId("plan-1"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-step-failure", result.journal().steps().getLast().requiredFailure().code());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("step 'open': boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact -> textFact(fact, "exceptionType", IllegalStateException.class.getName())));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_preservesPriorSuccessfulStepsBeforeUnexpectedRuntimeFailure() {
    try (LedgerPlanServiceTestSupport.DeclareRuntimeFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.DeclareRuntimeFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(
                      openBookStep("open"),
                      new LedgerStep.DeclareAccount(
                          stepId("cash"),
                          account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(2, result.journal().steps().size());
      assertEquals("open", result.journal().steps().get(0).stepId().value());
      assertEquals(LedgerStepStatus.SUCCEEDED, result.journal().steps().get(0).status());
      assertEquals("cash", result.journal().steps().get(1).stepId().value());
      assertEquals(
          "unexpected-step-failure", result.journal().steps().getLast().requiredFailure().code());
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenTransactionBeginFails() {
    try (LedgerPlanServiceTestSupport.BeginFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.BeginFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result = service.execute(new LedgerPlan(planId("plan-1"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(LedgerJournalKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(LedgerBoundaryPhase.BEGIN, result.journal().steps().getLast().boundaryPhase());
      assertTrue(
          result.journal().steps().getLast().requiredFailure().message().contains("during begin"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "phase", "begin")));
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenInitializationCheckThrows() {
    try (LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(
                      new LedgerStep.DeclareAccount(
                          stepId("cash"),
                          account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(LedgerJournalKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryPhase.INITIALIZATION_CHECK,
          result.journal().steps().getLast().boundaryPhase());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("during initialization-check before step 'cash': initialization boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "phase", "initialization-check")));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenCommitFailsAfterSuccessfulSteps() {
    try (LedgerPlanServiceTestSupport.CommitFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.CommitFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result = service.execute(new LedgerPlan(planId("plan-1"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(LedgerJournalKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(LedgerBoundaryPhase.COMMIT, result.journal().steps().getLast().boundaryPhase());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("during commit after step 'open': commit boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "phase", "commit")));
      assertTrue(bookSession.rollbackCalled());
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenRollbackFailsAfterDeterministicStepFailure() {
    try (LedgerPlanServiceTestSupport.RollbackFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.RollbackFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result =
          service.execute(
              new LedgerPlan(
                  planId("plan-1"),
                  List.of(
                      new LedgerStep.DeclareAccount(
                          stepId("cash"),
                          account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(LedgerJournalKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryPhase.ROLLBACK, result.journal().steps().getLast().boundaryPhase());
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "phase", "rollback")));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact ->
                      fact instanceof dev.erst.fingrind.contract.workflow.LedgerFact.Group group
                          && "priorFailure".equals(group.name())
                          && group.facts().stream()
                              .anyMatch(
                                  child ->
                                      textFact(
                                          child,
                                          "code",
                                          BookAdministrationRejection.wireCode(
                                              new BookAdministrationRejection
                                                  .BookNotInitialized())))));
    }
  }

  @Test
  void execute_returnsStructuredRejectionWhenRollbackFailsAfterUnexpectedStepFailure() {
    try (LedgerPlanServiceTestSupport.RuntimeRollbackFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.RuntimeRollbackFailingLedgerPlanSession()) {
      var service = service(bookSession);

      var result = service.execute(new LedgerPlan(planId("plan-1"), List.of(openBookStep("open"))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          "unexpected-plan-failure", result.journal().steps().getLast().requiredFailure().code());
      assertEquals(LedgerJournalKind.PLAN_BOUNDARY, result.journal().steps().getLast().kind());
      assertEquals(
          LedgerBoundaryPhase.ROLLBACK, result.journal().steps().getLast().boundaryPhase());
      assertTrue(
          result
              .journal()
              .steps()
              .getLast()
              .requiredFailure()
              .message()
              .contains("during rollback after step 'open': rollback boom"));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "phase", "rollback")));
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact ->
                      groupFact(
                          fact,
                          "priorFailure",
                          "code",
                          "unexpected-step-failure",
                          "message",
                          "Ledger plan execution failed unexpectedly during step 'open': boom")));
    }
  }
}
