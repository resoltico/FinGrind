package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EFFECTIVE_DATE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.FIXED_INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPageCursor;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountPageCursor;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PostingPageCursor;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.EffectiveDateRange;
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
                new PostingPageCursor(
                    EFFECTIVE_DATE,
                    FIXED_INSTANT,
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));

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
                    EFFECTIVE_DATE,
                    FIXED_INSTANT,
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))),
        BookReadQueryTranslator.fromPublished(postingsQuery));
    AccountLedgerQuery ledgerQuery =
        new AccountLedgerQuery(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS,
            25,
            Optional.of(
                new AccountLedgerPageCursor(
                    EFFECTIVE_DATE,
                    FIXED_INSTANT,
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"))));
    assertEquals(
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS,
            25,
            Optional.of(
                new dev.erst.fingrind.executor.bookkeeping.AccountLedgerCursor(
                    EFFECTIVE_DATE,
                    FIXED_INSTANT,
                    new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")))),
        BookReadQueryTranslator.fromPublished(ledgerQuery));
  }

  @Test
  void queryTranslator_translatesStatementQueries() {
    assertEquals(
        new FinancialPositionCriteria(Optional.of(EFFECTIVE_DATE), ComparativeSelection.none()),
        BookReadQueryTranslator.fromPublished(
            new FinancialPositionQuery(Optional.of(EFFECTIVE_DATE), ComparativeSelection.none())));
    assertEquals(
        new IncomeStatementCriteria(EFFECTIVE_DATE, EFFECTIVE_DATE, ComparativeSelection.none()),
        BookReadQueryTranslator.fromPublished(
            new IncomeStatementQuery(EFFECTIVE_DATE, EFFECTIVE_DATE, ComparativeSelection.none())));
    assertEquals(
        new ChangesInEquityCriteria(EFFECTIVE_DATE, EFFECTIVE_DATE, ComparativeSelection.none()),
        BookReadQueryTranslator.fromPublished(
            new ChangesInEquityQuery(EFFECTIVE_DATE, EFFECTIVE_DATE, ComparativeSelection.none())));
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
    AccountLedgerCriteria ledgerCriteria =
        new AccountLedgerCriteria(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(EFFECTIVE_DATE, EFFECTIVE_DATE),
            PostingCoverage.NON_CLOSING_POSTINGS,
            ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
            Optional.empty());
    assertEquals(ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT, ledgerCriteria.limit());
    assertEquals(Optional.empty(), ledgerCriteria.cursor());
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
        () ->
            new AccountRegistryQuery(
                ProtocolInteractionLimits.PAGE_LIMIT_MIN - 1, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccountRegistryQuery(
                ProtocolInteractionLimits.PAGE_LIMIT_MAX + 1, Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostingHistoryQuery(
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                ProtocolInteractionLimits.PAGE_LIMIT_MAX + 1,
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PostingHistoryQuery(
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                ProtocolInteractionLimits.PAGE_LIMIT_MIN - 1,
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PeriodSummaryCriteria(EFFECTIVE_DATE.plusDays(1), EFFECTIVE_DATE));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccountLedgerCriteria(
                CASH_ACCOUNT.accountCode(),
                EffectiveDateRange.unbounded(),
                PostingCoverage.ALL_POSTING_KINDS,
                ProtocolInteractionLimits.PAGE_LIMIT_MIN - 1,
                Optional.empty()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new AccountLedgerCriteria(
                CASH_ACCOUNT.accountCode(),
                EffectiveDateRange.unbounded(),
                PostingCoverage.ALL_POSTING_KINDS,
                ProtocolInteractionLimits.PAGE_LIMIT_MAX + 1,
                Optional.empty()));
  }
}
