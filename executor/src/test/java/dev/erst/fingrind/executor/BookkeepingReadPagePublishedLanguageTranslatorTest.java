package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_DEBIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REGISTERED_CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.postingFact;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountPage;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.postingPage;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadPagePublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers read-page publication from local bookkeeping views into the public contract. */
class BookkeepingReadPagePublishedLanguageTranslatorTest {
  @Test
  void pageTranslator_projectsAccountAndPostingPagesWithPaginationCursors() {
    var postingFact = postingFact("posting-1", "idem-1");

    assertEquals(
        accountPage(
            List.of(CASH_ACCOUNT),
            25,
            Optional.of(new AccountPageCursor(REVENUE_ACCOUNT.accountCode()))),
        BookkeepingReadPagePublishedLanguageTranslator.toPublished(
            bookIdentity(),
            new AccountRegistryPage(
                List.of(REGISTERED_CASH_ACCOUNT),
                25,
                Optional.of(new AccountRegistryCursor(REVENUE_ACCOUNT.accountCode())))));
    assertEquals(
        postingPage(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            List.of(BookkeepingPublishedLanguageTranslator.toPublished(postingFact)),
            20,
            Optional.of(
                new PostingPageCursor(EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))),
        BookkeepingReadPagePublishedLanguageTranslator.toPublished(
            bookIdentity(),
            new PostingHistoryQuery(
                Optional.of(CASH_ACCOUNT.accountCode()),
                EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
                20,
                Optional.of(PostingHistoryCursor.fromPosting(postingFact))),
            new PostingHistoryPage(
                List.of(postingFact),
                20,
                Optional.of(PostingHistoryCursor.fromPosting(postingFact)))));
  }

  @Test
  void pageTranslator_projectsAccountBalanceSnapshots() {
    AccountBalanceView view =
        new AccountBalanceView(
            REGISTERED_CASH_ACCOUNT,
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(EUR_DEBIT_BALANCE));

    assertEquals(
        new AccountBalanceSnapshot(
            bookIdentity(),
            CASH_ACCOUNT,
            Optional.of(EFFECTIVE_DATE),
            Optional.of(EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(EUR_DEBIT_BALANCE)),
        BookkeepingReadPagePublishedLanguageTranslator.toPublished(bookIdentity(), view));
  }
}
