package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.InteractionLimits;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers application-owned lifting from published read queries into local bookkeeping criteria. */
class BookReadQueryTranslatorTest {
  @Test
  void queryTranslator_translatesRegistryAndPostingQueries() {
    ListAccountsQuery accountsQuery =
        new ListAccountsQuery(25, Optional.of(new AccountPageCursor(CASH_ACCOUNT.accountCode())));
    ListPostingsQuery postingsQuery =
        new ListPostingsQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            20,
            Optional.of(
                new PostingPageCursor(EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("posting-1"))));

    assertEquals(
        new AccountRegistryQuery(
            25, Optional.of(new AccountRegistryCursor(CASH_ACCOUNT.accountCode()))),
        BookReadQueryTranslator.fromPublished(accountsQuery));
    assertEquals(
        new PostingHistoryQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            20,
            Optional.of(
                new PostingHistoryCursor(
                    EFFECTIVE_DATE, FIXED_INSTANT, new PostingId("posting-1")))),
        BookReadQueryTranslator.fromPublished(postingsQuery));
  }

  @Test
  void queryTranslator_translatesStatementQueries() {
    assertEquals(
        new FinancialPositionCriteria(Optional.of(EFFECTIVE_DATE)),
        BookReadQueryTranslator.fromPublished(
            new FinancialPositionQuery(Optional.of(EFFECTIVE_DATE))));
    assertEquals(
        new IncomeStatementCriteria(EFFECTIVE_DATE, EFFECTIVE_DATE),
        BookReadQueryTranslator.fromPublished(
            new IncomeStatementQuery(EFFECTIVE_DATE, EFFECTIVE_DATE)));
    assertEquals(
        new ChangesInEquityCriteria(EFFECTIVE_DATE, EFFECTIVE_DATE),
        BookReadQueryTranslator.fromPublished(
            new ChangesInEquityQuery(EFFECTIVE_DATE, EFFECTIVE_DATE)));
  }

  @Test
  void localReadCriteria_validateSharedKernelRangesAndPagingContracts() {
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS),
        AccountBalanceCriteria.unbounded(
            CASH_ACCOUNT.accountCode(), PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS),
        AccountBalanceCriteria.unbounded(CASH_ACCOUNT.accountCode()));
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.ALL_POSTING_KINDS),
        new AccountBalanceCriteria(CASH_ACCOUNT.accountCode(), EFFECTIVE_DATE, EFFECTIVE_DATE));
    assertEquals(
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS),
        new AccountBalanceCriteria(
            CASH_ACCOUNT.accountCode(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS),
        AccountLedgerCriteria.unbounded(
            CASH_ACCOUNT.accountCode(), PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.unbounded(),
            PostingCoverage.ALL_POSTING_KINDS),
        AccountLedgerCriteria.unbounded(CASH_ACCOUNT.accountCode()));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.ALL_POSTING_KINDS),
        new AccountLedgerCriteria(CASH_ACCOUNT.accountCode(), EFFECTIVE_DATE, EFFECTIVE_DATE));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS),
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            PostingCoverage.NON_CLOSING_POSTINGS));
    assertEquals(
        new PostingHistoryQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            20,
            Optional.empty()),
        new PostingHistoryQuery(
            Optional.of(CASH_ACCOUNT.accountCode()),
            EFFECTIVE_DATE,
            EFFECTIVE_DATE,
            20,
            Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccountRegistryQuery(InteractionLimits.PAGE_LIMIT_MIN - 1, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new AccountRegistryQuery(InteractionLimits.PAGE_LIMIT_MAX + 1, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostingHistoryQuery(
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                InteractionLimits.PAGE_LIMIT_MAX + 1,
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostingHistoryQuery(
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                InteractionLimits.PAGE_LIMIT_MIN - 1,
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PeriodSummaryCriteria(EFFECTIVE_DATE.plusDays(1), EFFECTIVE_DATE));
  }
}
