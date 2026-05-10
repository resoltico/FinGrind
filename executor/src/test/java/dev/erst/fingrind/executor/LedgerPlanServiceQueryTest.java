package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.FIXED_CLOCK;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.bookWithCommittedPosting;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.countFact;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.flagFact;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.groupFact;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.monetaryAmount;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.planId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.postEntryCommand;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.service;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.stepId;
import static dev.erst.fingrind.executor.LedgerPlanServiceTestSupport.textFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountPageCursor;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.LedgerFact;
import dev.erst.fingrind.contract.LedgerPlan;
import dev.erst.fingrind.contract.LedgerPlanStatus;
import dev.erst.fingrind.contract.LedgerStep;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering query-specific behavior in {@link LedgerPlanService}. */
class LedgerPlanServiceQueryTest {
  @Test
  void execute_recordsStructuredQueryFactsForAccountAndPostingSteps() {
    try (InMemoryBookSession bookSession = bookWithCommittedPosting()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-query-shape"),
                      List.of(
                          new LedgerStep.ListAccounts(
                              stepId("accounts"), new ListAccountsQuery(1, Optional.empty())),
                          new LedgerStep.GetPosting(stepId("get"), new PostingId("posting-1")),
                          new LedgerStep.ListPostings(
                              stepId("postings"),
                              new ListPostingsQuery(
                                  Optional.empty(), null, null, 50, Optional.empty())),
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              new AccountBalanceQuery(new AccountCode("1000"), null, null)))));

      assertEquals(LedgerPlanStatus.SUCCEEDED, result.status());

      List<LedgerFact> listAccountFacts = result.journal().steps().get(0).facts();
      assertTrue(listAccountFacts.stream().anyMatch(fact -> countFact(fact, "count", 1)));
      assertTrue(listAccountFacts.stream().anyMatch(fact -> countFact(fact, "pageLimit", 1)));
      assertTrue(listAccountFacts.stream().anyMatch(fact -> flagFact(fact, "hasMore", true)));
      assertTrue(
          listAccountFacts.stream()
              .anyMatch(
                  fact ->
                      textFact(
                          fact,
                          "nextCursor",
                          AccountPageCursor.fromAccount(
                                  new DeclaredAccount(
                                      new AccountCode("1000"),
                                      new AccountName("Cash"),
                                      NormalBalance.DEBIT,
                                      true,
                                      FIXED_CLOCK.instant()))
                              .wireValue())));
      assertTrue(
          listAccountFacts.stream()
              .anyMatch(
                  fact ->
                      groupFact(fact, "account", "accountCode", "1000", "accountName", "Cash")));

      List<LedgerFact> getPostingFacts = result.journal().steps().get(1).facts();
      assertTrue(
          getPostingFacts.stream().anyMatch(fact -> textFact(fact, "postingId", "posting-1")));
      assertTrue(
          getPostingFacts.stream()
              .anyMatch(
                  fact ->
                      groupFact(fact, "provenance", "actorId", "actor-1", "sourceChannel", "CLI")));
      assertTrue(
          getPostingFacts.stream()
              .anyMatch(
                  fact ->
                      groupFact(
                          fact,
                          "line",
                          "accountCode",
                          "1000",
                          "amount",
                          monetaryAmount("EUR", "10.00"))));

      List<LedgerFact> listPostingFacts = result.journal().steps().get(2).facts();
      assertTrue(listPostingFacts.stream().anyMatch(fact -> countFact(fact, "count", 1)));
      assertTrue(listPostingFacts.stream().anyMatch(fact -> countFact(fact, "pageLimit", 50)));
      assertTrue(listPostingFacts.stream().anyMatch(fact -> flagFact(fact, "hasMore", false)));
      assertTrue(
          listPostingFacts.stream()
              .anyMatch(
                  fact ->
                      fact instanceof LedgerFact.Group group
                          && "posting".equals(group.name())
                          && group.facts().stream()
                              .anyMatch(
                                  child ->
                                      textFact(
                                          child,
                                          "postingId",
                                          PostingPageCursor.fromPosting(
                                                  BookkeepingPublishedLanguageTranslator
                                                      .toPublished(
                                                          bookSession
                                                              .findPosting(
                                                                  new PostingId("posting-1"))
                                                              .orElseThrow()))
                                              .postingId()
                                              .value()))));

      List<LedgerFact> balanceFacts = result.journal().steps().get(3).facts();
      assertTrue(
          balanceFacts.stream()
              .anyMatch(
                  fact ->
                      groupFact(fact, "account", "accountCode", "1000", "accountName", "Cash")));
      assertTrue(balanceFacts.stream().anyMatch(fact -> countFact(fact, "bucketCount", 1)));
    }
  }

  @Test
  void execute_reportsPreflightAndQueryRejections() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          FIXED_CLOCK.instant());

      var preflightResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-preflight"),
                      List.of(
                          new LedgerStep.PreflightEntry(
                              stepId("preflight"), postEntryCommand("idem-2")))));

      assertEquals(LedgerPlanStatus.REJECTED, preflightResult.status());
      assertEquals(
          dev.erst.fingrind.contract.PostingRejection.wireCode(
              new dev.erst.fingrind.contract.PostingRejection.AccountStateViolations(
                  List.of(
                      new dev.erst.fingrind.contract.PostingRejection.UnknownAccount(
                          new AccountCode("2000"))))),
          preflightResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      var getPostingResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-get"),
                      List.of(
                          new LedgerStep.GetPosting(
                              stepId("get"), new PostingId("posting-missing")))));

      assertEquals(LedgerPlanStatus.REJECTED, getPostingResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.PostingNotFound(new PostingId("posting-missing"))),
          getPostingResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      ListPostingsQuery missingAccountQuery =
          new ListPostingsQuery(
              Optional.of(new AccountCode("9999")), null, null, 50, Optional.empty());
      var listPostingsResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-list-postings"),
                      List.of(
                          new LedgerStep.ListPostings(stepId("postings"), missingAccountQuery))));

      assertEquals(LedgerPlanStatus.REJECTED, listPostingsResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          listPostingsResult.journal().steps().getLast().requiredFailure().code());
    }

    try (InMemoryBookSession bookSession = initializedBook()) {
      var balanceResult =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-balance"),
                      List.of(
                          new LedgerStep.AccountBalance(
                              stepId("balance"),
                              new AccountBalanceQuery(new AccountCode("9999"), null, null)))));

      assertEquals(LedgerPlanStatus.REJECTED, balanceResult.status());
      assertEquals(
          BookQueryRejection.wireCode(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          balanceResult.journal().steps().getLast().requiredFailure().code());
    }
  }

  @Test
  void execute_reportsListAccountsRejectionFromQuerySeam() {
    try (var bookSession =
        new LedgerPlanServiceTestSupport.ListAccountsRejectingLedgerPlanSession()) {
      var result =
          service(bookSession)
              .execute(
                  new LedgerPlan(
                      planId("plan-list-accounts"),
                      List.of(
                          new LedgerStep.OpenBook(stepId("open")),
                          new LedgerStep.ListAccounts(
                              stepId("accounts"), new ListAccountsQuery(50, Optional.empty())))));

      assertEquals(LedgerPlanStatus.REJECTED, result.status());
      assertEquals(
          BookQueryRejection.wireCode(new BookQueryRejection.BookNotInitialized()),
          result.journal().steps().getLast().requiredFailure().code());
    }
  }
}
