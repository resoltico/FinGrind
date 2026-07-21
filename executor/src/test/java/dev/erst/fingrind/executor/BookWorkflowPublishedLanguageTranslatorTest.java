package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.workflow.LedgerPlan;
import dev.erst.fingrind.contract.workflow.LedgerPlanId;
import dev.erst.fingrind.contract.workflow.LedgerStep;
import dev.erst.fingrind.contract.workflow.LedgerStepId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.workflow.BookWorkflowPlan;
import dev.erst.fingrind.executor.workflow.BookWorkflowPublishedLanguageTranslator;
import dev.erst.fingrind.executor.workflow.BookWorkflowStep;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage tests for workflow translation branches that carry published query cursors. */
class BookWorkflowPublishedLanguageTranslatorTest {
  @Test
  void fromPublishedLiftsAccountRegistryCursorIntoLocalWorkflowStep() {
    LedgerStep.ListAccounts listAccountsStep =
        new LedgerStep.ListAccounts(
            new LedgerStepId("list-accounts"),
            new ListAccountsQuery(10, Optional.of(new AccountPageCursor(new AccountCode("1000")))));

    BookWorkflowStep.ListAccounts translated =
        assertInstanceOf(BookWorkflowStep.ListAccounts.class, workflowStep(listAccountsStep));

    assertEquals(10, translated.query().limit());
    assertEquals(
        Optional.of(new AccountRegistryCursor(new AccountCode("1000"))),
        translated.query().cursor());
  }

  @Test
  void fromPublishedLiftsPostingHistoryCursorIntoLocalWorkflowStep() {
    LedgerStep.ListPostings listPostingsStep =
        new LedgerStep.ListPostings(
            new LedgerStepId("list-postings"),
            new ListPostingsQuery(
                Optional.of(new AccountCode("1000")),
                EffectiveDateRange.of(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31")),
                10,
                Optional.of(
                    new PostingPageCursor(
                        LocalDate.parse("2026-01-15"),
                        Instant.parse("2026-01-15T10:15:30Z"),
                        new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))));

    BookWorkflowStep.ListPostings translated =
        assertInstanceOf(BookWorkflowStep.ListPostings.class, workflowStep(listPostingsStep));

    assertEquals(Optional.of(new AccountCode("1000")), translated.query().accountCode());
    assertEquals(
        Optional.of(
            new PostingHistoryCursor(
                LocalDate.parse("2026-01-15"),
                Instant.parse("2026-01-15T10:15:30Z"),
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))),
        translated.query().cursor());
  }

  private static BookWorkflowStep workflowStep(LedgerStep step) {
    BookWorkflowPlan plan =
        BookWorkflowPublishedLanguageTranslator.fromPublished(
            new LedgerPlan(new LedgerPlanId("plan-1"), List.of(step)));
    return plan.steps().getFirst();
  }
}
