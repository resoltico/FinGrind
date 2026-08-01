package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.account;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.inspectBookStep;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.readOnlyService;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanAttestationDisposition;
import dev.erst.fingrind.contract.workflow.LedgerPlanResult;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.NormalBalance;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for read-only ledger-plan execution and its transaction boundary. */
class LedgerPlanReadOnlyServiceWorkflowTest {
  @Test
  void readOnlyService_commitsInspectionAndAssertionWithoutAnAttestationCredential() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_CLOCK.instant());
      LedgerPlanResult.Succeeded result =
          assertInstanceOf(
              LedgerPlanResult.Succeeded.class,
              readOnlyService(bookSession)
                  .execute(
                      new LedgerPlan(
                          planId("read-only-plan"),
                          List.of(
                              inspectBookStep("inspect"),
                              new LedgerStep.ListAccounts(
                                  stepId("accounts"), new ListAccountsQuery(50, Optional.empty())),
                              new LedgerStep.Assert(
                                  stepId("assert-empty"),
                                  new LedgerAssertion.AccountDeclared(new AccountCode("1000")))))));

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status());
      assertEquals(LedgerPlanAttestationDisposition.READ_ONLY, result.attestationDisposition());
      assertNull(result.attestationCommit());
    }
  }

  @Test
  void readOnlyService_rethrowsCanonicalContractFailureAndRollsBackExactlyOnce() {
    ContractFailureException expected =
        new ContractFailureException(ContractErrors.unsupportedBookFormatVersionFailure(7, 8));
    try (LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession bookSession =
        new LedgerPlanServiceTestSupport.InitializationCheckFailingLedgerPlanSession(expected)) {
      ContractFailureException actual =
          assertThrows(
              ContractFailureException.class,
              () ->
                  readOnlyService(bookSession)
                      .execute(
                          new LedgerPlan(
                              planId("read-only-format"), List.of(inspectBookStep("open")))));

      assertSame(expected, actual);
      assertTrue(bookSession.rollbackCalled());
      assertEquals(1, bookSession.rollbackCalls());
    }
  }

  @Test
  void readOnlyService_rejectsMutatingPlansBeforeOpeningTheirTransaction() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      var result =
          readOnlyService(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("read-only-mutation"),
                      List.of(
                          inspectBookStep("inspect"),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"),
                              account("1000", "Cash", AccountType.ASSET, NormalBalance.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(1, result.journal().steps().size());
      assertEquals(
          "read-only-plan-mutation-forbidden",
          result.journal().steps().getLast().requiredFailure().code());
      assertTrue(
          result.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "stepId", "cash")));
      assertTrue(bookSession.allAccounts().isEmpty());
    }
  }

  @Test
  void readOnlyService_projectsFailedAssertionsWithoutAnAttestationDisposition() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      LedgerPlanResult.AssertionFailed result =
          assertInstanceOf(
              LedgerPlanResult.AssertionFailed.class,
              readOnlyService(bookSession)
                  .execute(
                      new LedgerPlan(
                          planId("read-only-assertion-failure"),
                          List.of(
                              new LedgerStep.Assert(
                                  stepId("missing-account"),
                                  new LedgerAssertion.AccountDeclared(new AccountCode("1000")))))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, result.status());
      assertEquals(1, result.journal().steps().size());
    }
  }
}
