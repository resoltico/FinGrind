package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.account;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.assertAssertionFailure;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.bookWithCommittedPosting;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.service;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.LedgerAssertion;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests covering assertion-specific behavior in {@link LedgerPlanService}. */
class LedgerPlanServiceAssertionTest {
  @Test
  void execute_rollsBackOnAssertionFailure() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-1"),
                      List.of(
                          new LedgerStep.OpenBook(stepId("open")),
                          new LedgerStep.DeclareAccount(
                              stepId("cash"), account("1000", "Cash", NormalBalance.DEBIT)),
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
      assertFalse(bookSession.isInitialized());
    }
  }

  @Test
  void execute_reportsSpecificAssertionFailures() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
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
              new Money(new CurrencyCode("USD"), BigDecimal.ZERO),
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
                                  new Money(new CurrencyCode("EUR"), BigDecimal.TEN),
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
                                  new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                                  BalanceSide.CREDIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, mismatchResult.status());
      assertEquals(
          "assertion-failed", mismatchResult.journal().steps().getLast().requiredFailure().code());
      assertTrue(
          mismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "expectedNetAmount", "10")));
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
                                  new Money(new CurrencyCode("EUR"), new BigDecimal("9.00")),
                                  BalanceSide.DEBIT)))));

      assertEquals(LedgerPlanStatus.ASSERTION_FAILED, amountMismatchResult.status());
      assertTrue(
          amountMismatchResult.journal().steps().getLast().requiredFailure().facts().stream()
              .anyMatch(fact -> textFact(fact, "actualNetAmount", "10")));
    }
  }
}
