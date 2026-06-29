package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountTaxonomy;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.account;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.assertAssertionFailure;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.bookWithCommittedPosting;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.monetaryAmount;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.moneyFact;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.openBookStep;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.service;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.workflow.LedgerAssertion;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanStatus;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.workflow.LedgerPlanAssertionEvaluator;
import dev.erst.fingrind.executor.workflow.LedgerPlanStepOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests covering assertion-specific behavior in {@link LedgerPlanService}. */
class LedgerPlanServiceAssertionTest {
  private static final BookkeepingReadStore REJECTED_LOOKUP_BOOK_STORE =
      new LedgerPlanServiceTestSupport.DelegatingAtomicBookStore() {
        @Override
        public BookLifecycleInspection inspectBook() {
          return new BookLifecycleInspection.Missing(1);
        }
      };

  @Test
  void execute_rollsBackOnAssertionFailure() {
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
                          new LedgerStep.Assert(
                              stepId("missing-posting"),
                              new LedgerAssertion.PostingExists(
                                  new PostingId("posting-missing"))))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, result.status());
      assertEquals(3, result.journal().steps().size());
      assertEquals("open", result.journal().steps().get(0).stepId().value());
      assertEquals("cash", result.journal().steps().get(1).stepId().value());
      assertEquals("missing-posting", result.journal().steps().get(2).stepId().value());
      assertEquals("assertion-failed", result.journal().steps().getLast().requiredFailure().code());
      assertFalse(bookSession.inspectBook().initialized());
    }
  }

  @Test
  void execute_reportsSpecificAssertionFailures() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          accountTaxonomy(AccountType.ASSET, NormalBalance.DEBIT),
          FIXED_CLOCK.instant());
      bookSession.deactivateAccount(new AccountCode("1000"));

      assertAssertionFailure(
          bookSession, new LedgerAssertion.AccountDeclared(new AccountCode("9999")));
      assertAssertionFailure(
          bookSession, new LedgerAssertion.AccountActive(new AccountCode("9999")));
      assertAssertionFailure(
          bookSession, new LedgerAssertion.AccountActive(new AccountCode("1000")));
      assertAssertionFailure(
          bookSession,
          new LedgerAssertion.AccountBalanceEquals(
              new AccountCode("1000"),
              null,
              null,
              Money.zero(CurrencyUnit.of("USD")),
              BalanceSide.ZERO));
    }
  }

  @Test
  void execute_reportsAssertionQueryRejectionsAndBalanceMismatches() {
    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      var rejectedQueryResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-query"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("9999"),
                                  null,
                                  null,
                                  Money.parse("EUR", "10.00"),
                                  BalanceSide.DEBIT)))));

      assertEquals(LedgerPlanStatus.REJECTED, rejectedQueryResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          rejectedQueryResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      var mismatchResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-mismatch"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  null,
                                  null,
                                  Money.parse("EUR", "10.00"),
                                  BalanceSide.CREDIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, mismatchResult.status());
      assertEquals(
          "assertion-failed", mismatchResult.journal().steps().getLast().requiredFailure().code());
      assertTrue(
          mismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact -> moneyFact(fact, "expectedNetAmount", monetaryAmount("EUR", "10.00"))));
      assertTrue(
          mismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "expectedBalanceSide", "CREDIT")));
    }

    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      var amountMismatchResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-amount-mismatch"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-balance"),
                              new LedgerAssertion.AccountBalanceEquals(
                                  new AccountCode("1000"),
                                  null,
                                  null,
                                  Money.parse("EUR", "9.00"),
                                  BalanceSide.DEBIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, amountMismatchResult.status());
      assertTrue(
          amountMismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(
                  fact -> moneyFact(fact, "actualNetAmount", monetaryAmount("EUR", "10.00"))));
    }
  }

  @Test
  void execute_routesLifecycleRejectionsThroughAssertionLookupVariants() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var accountDeclaredResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-account-declared-rejected"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-account-declared"),
                              new LedgerAssertion.AccountDeclared(new AccountCode("1000"))))));
      assertEquals(LedgerPlanStatus.REJECTED, accountDeclaredResult.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          accountDeclaredResult.journal().steps().getLast().requiredFailure().code());

      var accountActiveResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-account-active-rejected"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-account-active"),
                              new LedgerAssertion.AccountActive(new AccountCode("1000"))))));
      assertEquals(LedgerPlanStatus.REJECTED, accountActiveResult.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          accountActiveResult.journal().steps().getLast().requiredFailure().code());

      var postingExistsResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-assert-posting-exists-rejected"),
                      List.of(
                          new LedgerStep.Assert(
                              stepId("assert-posting-exists"),
                              new LedgerAssertion.PostingExists(new PostingId("posting-1"))))));
      assertEquals(LedgerPlanStatus.REJECTED, postingExistsResult.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          postingExistsResult.journal().steps().getLast().requiredFailure().code());
    }
  }

  @Test
  void evaluate_routesLifecycleRejectionsThroughLookupAssertionsDirectly() {
    BookkeepingReadService rejectedReadService =
        new BookkeepingReadService(REJECTED_LOOKUP_BOOK_STORE);

    assertEquals(
        BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
        ((LedgerPlanStepOutcome.Rejected)
                LedgerPlanAssertionEvaluator.evaluate(
                    rejectedReadService,
                    new dev.erst.fingrind.executor.workflow.BookWorkflowAssertion.AccountDeclared(
                        new AccountCode("1000"))))
            .failure()
            .code());

    assertEquals(
        BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
        ((LedgerPlanStepOutcome.Rejected)
                LedgerPlanAssertionEvaluator.evaluate(
                    rejectedReadService,
                    new dev.erst.fingrind.executor.workflow.BookWorkflowAssertion.AccountActive(
                        new AccountCode("1000"))))
            .failure()
            .code());

    assertEquals(
        BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
        ((LedgerPlanStepOutcome.Rejected)
                LedgerPlanAssertionEvaluator.evaluate(
                    rejectedReadService,
                    new dev.erst.fingrind.executor.workflow.BookWorkflowAssertion.PostingExists(
                        new PostingId("posting-1"))))
            .failure()
            .code());
  }
}
