package dev.erst.fingrind.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests for reporting contract value types and deterministic CLI error descriptors. */
@NullUnmarked
class ReportingContractTypesTest {
  private static final DeclaredAccount CASH_ACCOUNT =
      new DeclaredAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          true,
          Instant.parse("2026-04-07T10:15:30Z"));

  private static final CurrencyBalance EUR_DEBIT_BALANCE =
      new CurrencyBalance(
          new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
          new Money(new CurrencyCode("EUR"), BigDecimal.ZERO),
          new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
          BalanceSide.DEBIT);

  @Test
  void reportingQueriesReportsAndResults_preserveCanonicalState() {
    TrialBalanceQuery trialBalanceQuery =
        new TrialBalanceQuery(Optional.of(LocalDate.parse("2026-04-30")));
    TrialBalanceReport trialBalanceReport =
        new TrialBalanceReport(
            trialBalanceQuery.effectiveDateTo(),
            List.of(new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE)));
    TrialBalanceResult.Reported reportedTrialBalance =
        new TrialBalanceResult.Reported(trialBalanceReport);
    TrialBalanceResult.Rejected rejectedTrialBalance =
        new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());

    AccountLedgerQuery accountLedgerQuery =
        new AccountLedgerQuery(
            CASH_ACCOUNT.accountCode(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"));
    AccountLedgerEntry accountLedgerEntry =
        new AccountLedgerEntry(
            postingFact("posting-1", "idem-1"),
            EUR_DEBIT_BALANCE,
            new Money(new CurrencyCode("EUR"), new BigDecimal("15.00")),
            BalanceSide.DEBIT);
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            CASH_ACCOUNT,
            accountLedgerQuery.effectiveDateRange(),
            List.of(),
            List.of(accountLedgerEntry),
            List.of(EUR_DEBIT_BALANCE));
    AccountLedgerResult.Reported reportedAccountLedger =
        new AccountLedgerResult.Reported(accountLedgerReport);
    AccountLedgerResult.Rejected rejectedAccountLedger =
        new AccountLedgerResult.Rejected(
            new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode()));

    PeriodSummaryQuery periodSummaryQuery =
        new PeriodSummaryQuery(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));
    PeriodSummaryReport periodSummaryReport =
        new PeriodSummaryReport(
            periodSummaryQuery.effectiveDateFrom(),
            periodSummaryQuery.effectiveDateTo(),
            1,
            2,
            1,
            List.of(new PeriodCurrencySummary(EUR_DEBIT_BALANCE)),
            List.of(new PeriodAccountActivityRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE)));
    PeriodSummaryResult.Reported reportedPeriodSummary =
        new PeriodSummaryResult.Reported(periodSummaryReport);
    PeriodSummaryResult.Rejected rejectedPeriodSummary =
        new PeriodSummaryResult.Rejected(new BookQueryRejection.BookNotInitialized());

    ListAccountsResult.Listed listedAccounts =
        new ListAccountsResult.Listed(new AccountPage(List.of(CASH_ACCOUNT), 50, Optional.empty()));
    ListAccountsResult.Rejected rejectedAccounts =
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized());
    GetPostingResult.Found foundPosting =
        new GetPostingResult.Found(postingFact("posting-3", "idem-3"));
    GetPostingResult.Rejected rejectedPosting =
        new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized());
    ListPostingsResult.Listed listedPostings =
        new ListPostingsResult.Listed(
            new PostingPage(List.of(postingFact("posting-4", "idem-4")), 10, Optional.empty()));
    ListPostingsResult.Rejected rejectedPostings =
        new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized());
    AccountBalanceResult.Reported reportedBalance =
        new AccountBalanceResult.Reported(
            new AccountBalanceSnapshot(
                CASH_ACCOUNT, Optional.empty(), Optional.empty(), List.of(EUR_DEBIT_BALANCE)));
    AccountBalanceResult.Rejected rejectedBalance =
        new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());

    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), trialBalanceQuery.effectiveDateTo());
    assertEquals(
        List.of(new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE)), trialBalanceReport.rows());
    assertSame(trialBalanceReport, reportedTrialBalance.report());
    assertEquals(
        "query-book-not-initialized",
        BookQueryRejection.wireCode(rejectedTrialBalance.rejection()));

    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")), accountLedgerQuery.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), accountLedgerQuery.effectiveDateTo());
    assertSame(accountLedgerReport, reportedAccountLedger.report());
    assertEquals(
        CASH_ACCOUNT.accountCode(),
        ((BookQueryRejection.UnknownAccount) rejectedAccountLedger.rejection()).accountCode());
    assertEquals(accountLedgerEntry, accountLedgerReport.entries().getFirst());

    assertEquals(LocalDate.parse("2026-04-01"), periodSummaryQuery.effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-04-30"), periodSummaryQuery.effectiveDateTo());
    assertSame(periodSummaryReport, reportedPeriodSummary.report());
    assertEquals(
        "query-book-not-initialized",
        BookQueryRejection.wireCode(rejectedPeriodSummary.rejection()));
    assertEquals(1, periodSummaryReport.accountsTouched());

    assertEquals("listed", listedAccounts.fold(ignored -> "listed", ignored -> "rejected"));
    assertEquals("rejected", rejectedAccounts.fold(ignored -> "listed", ignored -> "rejected"));
    assertEquals("found", foundPosting.fold(ignored -> "found", ignored -> "rejected"));
    assertEquals("rejected", rejectedPosting.fold(ignored -> "found", ignored -> "rejected"));
    assertEquals("listed", listedPostings.fold(ignored -> "listed", ignored -> "rejected"));
    assertEquals("rejected", rejectedPostings.fold(ignored -> "listed", ignored -> "rejected"));
    assertEquals("reported", reportedBalance.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals("rejected", rejectedBalance.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "reported", reportedTrialBalance.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "rejected", rejectedTrialBalance.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "reported", reportedAccountLedger.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "rejected", rejectedAccountLedger.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "reported", reportedPeriodSummary.fold(ignored -> "reported", ignored -> "rejected"));
    assertEquals(
        "rejected", rejectedPeriodSummary.fold(ignored -> "reported", ignored -> "rejected"));
  }

  @Test
  void reportingValueTypes_rejectInvalidInputs() {
    assertThrows(NullPointerException.class, () -> new TrialBalanceQuery(null));
    assertEquals(List.of(), new TrialBalanceReport(Optional.empty(), null).rows());
    assertThrows(NullPointerException.class, () -> new TrialBalanceRow(null, EUR_DEBIT_BALANCE));
    assertThrows(NullPointerException.class, () -> new TrialBalanceResult.Reported(null));
    assertThrows(NullPointerException.class, () -> new TrialBalanceResult.Rejected(null));

    assertThrows(
        NullPointerException.class,
        () -> new AccountLedgerQuery(null, EffectiveDateRange.unbounded()));
    assertThrows(
        NullPointerException.class,
        () -> new AccountLedgerQuery(CASH_ACCOUNT.accountCode(), (EffectiveDateRange) null));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountLedgerReport(
                null, EffectiveDateRange.unbounded(), List.of(), List.of(), List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountLedgerEntry(
                null, EUR_DEBIT_BALANCE, EUR_DEBIT_BALANCE.netAmount(), BalanceSide.DEBIT));
    assertThrows(NullPointerException.class, () -> new AccountLedgerResult.Reported(null));
    assertThrows(NullPointerException.class, () -> new AccountLedgerResult.Rejected(null));

    assertThrows(
        IllegalArgumentException.class,
        () -> new PeriodSummaryQuery(LocalDate.parse("2026-04-30"), LocalDate.parse("2026-04-01")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                0,
                0,
                0,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                -1,
                0,
                0,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                0,
                -1,
                0,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                0,
                0,
                -1,
                List.of(),
                List.of()));
    assertThrows(NullPointerException.class, () -> new PeriodCurrencySummary(null));
    assertThrows(
        NullPointerException.class, () -> new PeriodAccountActivityRow(null, EUR_DEBIT_BALANCE));
    assertThrows(NullPointerException.class, () -> new PeriodSummaryResult.Reported(null));
    assertThrows(NullPointerException.class, () -> new PeriodSummaryResult.Rejected(null));

    AccountBalanceResult.Reported reportedBalance =
        new AccountBalanceResult.Reported(
            new AccountBalanceSnapshot(
                CASH_ACCOUNT, Optional.empty(), Optional.empty(), List.of(EUR_DEBIT_BALANCE)));
    assertThrows(
        NullPointerException.class, () -> reportedBalance.fold(null, ignored -> "rejected"));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized())
                .fold(ignored -> "reported", null));

    AtomicInteger foldCounter = new AtomicInteger();
    ListAccountsResult.Listed listedAccounts =
        new ListAccountsResult.Listed(new AccountPage(List.of(CASH_ACCOUNT), 50, Optional.empty()));
    listedAccounts.fold(
        ignored -> {
          foldCounter.incrementAndGet();
          return "listed";
        },
        ignored -> "rejected");
    assertEquals(1, foldCounter.get());
  }

  @Test
  void contractErrorsAndDecisions_exposeCanonicalDeterministicFailureMetadata() {
    List<ContractResponse.ErrorDescriptor> descriptors = ContractErrors.descriptors();
    ContractFailure withoutCause =
        ContractErrors.Descriptor.INVALID_PAGE_CURSOR.failure(
            "Bad cursor", "Retry without --cursor.", "--cursor");
    ContractFailure withCause =
        ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED.failure(
            "Wrong key", "Use the correct key file.", "--book-key-file");
    ContractDecision<String> accepted = ContractDecision.accepted("ok");
    ContractDecision<String> rejected = ContractDecision.rejected(withoutCause);

    assertEquals(10, descriptors.size());
    assertEquals("unknown-command", descriptors.getFirst().code());
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "interactive-prompt-unavailable".equals(descriptor.code())));
    assertEquals("invalid-page-cursor", ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code());
    assertTrue(
        ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED
            .description()
            .contains("authenticate"));

    assertSame(ContractErrors.Descriptor.INVALID_PAGE_CURSOR, withoutCause.descriptor());
    assertEquals("invalid-page-cursor", withoutCause.code());
    assertEquals("Retry without --cursor.", withoutCause.hint());
    assertEquals("--cursor", withoutCause.argument());
    assertEquals("Bad cursor", withoutCause.message());

    assertSame(ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED, withCause.descriptor());
    assertEquals("book-authentication-failed", withCause.code());
    assertEquals("Use the correct key file.", withCause.hint());
    assertEquals("--book-key-file", withCause.argument());
    assertEquals("accepted:ok", accepted.fold(value -> "accepted:" + value, ignored -> "rejected"));
    assertEquals(
        "rejected:invalid-page-cursor",
        rejected.fold(ignored -> "accepted", failure -> "rejected:" + failure.code()));
    assertEquals("ok", accepted.requireAccepted());
    assertSame(withoutCause, rejected.requireRejected());
    assertEquals(
        "Bad cursor",
        assertThrows(IllegalStateException.class, rejected::requireAccepted).getMessage());
    assertEquals(
        "Expected a rejected contract decision.",
        assertThrows(IllegalStateException.class, accepted::requireRejected).getMessage());

    assertThrows(
        NullPointerException.class, () -> new ContractFailure(null, "message", null, null));
    assertThrows(NullPointerException.class, () -> ContractDecision.accepted(null));
    assertThrows(NullPointerException.class, () -> accepted.fold(null, ignored -> "rejected"));
    assertThrows(NullPointerException.class, () -> accepted.fold(value -> value, null));
  }

  @Test
  void descriptorEnums_publishStableWireValuesAndLegacyBooleanMappings() {
    assertEquals(
        "requires-open-book",
        ContractResponse.InitializationRequirement.REQUIRES_OPEN_BOOK.wireValue());
    assertEquals(
        "requires-open-book",
        ContractResponse.InitializationRequirement.REQUIRES_OPEN_BOOK.toString());
    assertEquals("guaranteed", ContractResponse.CommitGuarantee.GUARANTEED.wireValue());
    assertEquals("guaranteed", ContractResponse.CommitGuarantee.GUARANTEED.toString());
    assertEquals("not-guaranteed", ContractResponse.CommitGuarantee.NOT_GUARANTEED.wireValue());
    assertEquals("not-guaranteed", ContractResponse.CommitGuarantee.NOT_GUARANTEED.toString());
    assertEquals(
        ContractResponse.CommitGuarantee.GUARANTEED,
        ContractResponse.CommitGuarantee.fromGuaranteed(true));
    assertEquals(
        ContractResponse.CommitGuarantee.NOT_GUARANTEED,
        ContractResponse.CommitGuarantee.fromGuaranteed(false));
    assertEquals("verified", SqliteCompileOptionsVerificationStatus.VERIFIED.wireValue());
    assertEquals("verified", SqliteCompileOptionsVerificationStatus.VERIFIED.toString());
    assertEquals("not-verified", SqliteCompileOptionsVerificationStatus.NOT_VERIFIED.wireValue());
    assertEquals("not-verified", SqliteCompileOptionsVerificationStatus.NOT_VERIFIED.toString());
  }

  private static PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("15.00"))),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("15.00"))))),
        PostingLineage.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.HUMAN,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("correlation-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }
}
