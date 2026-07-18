package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.DeclaredAccount;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.protocol.ProtocolInteractionLimits;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.ComparativeSelection;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Unit tests for reporting contract value types and deterministic CLI error descriptors. */
class ReportingContractTypesTest {
  private static final DeclaredAccount CASH_ACCOUNT =
      ContractFixtures.declaredAccount(
          "1000", "Cash", AccountType.ASSET, true, Instant.parse("2026-04-07T10:15:30Z"));
  private static final CurrencyBalance EUR_DEBIT_BALANCE =
      CurrencyBalance.ofTotals(Money.parse("EUR", "15.00"), Money.parse("EUR", "0.00"));

  @Test
  void reportingQueriesReportsAndResults_preserveCanonicalState() {
    TrialBalanceQuery trialBalanceQuery =
        new TrialBalanceQuery(
            Optional.of(LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            ComparativeSelection.none());
    TrialBalanceReport trialBalanceReport =
        new TrialBalanceReport(
            ContractFixtures.bookIdentity(),
            trialBalanceQuery.effectiveDateAsOf(),
            trialBalanceQuery.effectiveDateAsOf(),
            EffectiveDateRange.of(null, LocalDate.parse("2025-04-30")),
            trialBalanceQuery.postingCoverage(),
            List.of(new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE)),
            List.of(EUR_DEBIT_BALANCE),
            false,
            List.of(new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE)),
            List.of(EUR_DEBIT_BALANCE),
            false);
    TrialBalanceResult.Reported reportedTrialBalance =
        new TrialBalanceResult.Reported(trialBalanceReport);
    TrialBalanceResult.Rejected rejectedTrialBalance =
        new TrialBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());
    AccountLedgerQuery accountLedgerQuery =
        new AccountLedgerQuery(
            CASH_ACCOUNT.accountCode(),
            EffectiveDateRange.of(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
            Optional.empty());
    AccountLedgerEntry accountLedgerEntry =
        new AccountLedgerEntry(
            postingFact("posting-1", "idem-1"),
            EUR_DEBIT_BALANCE,
            Money.parse("EUR", "15.00"),
            BalanceSide.DEBIT);
    AccountLedgerReport accountLedgerReport =
        new AccountLedgerReport(
            ContractFixtures.bookIdentity(),
            CASH_ACCOUNT,
            accountLedgerQuery.effectiveDateRange(),
            PostingCoverage.ALL_POSTING_KINDS,
            new AccountLedgerPagination(
                accountLedgerQuery.limit(), accountLedgerQuery.cursor(), Optional.empty()),
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
            ContractFixtures.bookIdentity(),
            periodSummaryQuery.effectiveDateFrom(),
            periodSummaryQuery.effectiveDateTo(),
            PostingCoverage.ALL_POSTING_KINDS,
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
        new ListAccountsResult.Listed(
            new ListAccountsQuery(50, Optional.empty()),
            ContractFixtures.accountPage(List.of(CASH_ACCOUNT), 50, Optional.empty()));
    ListAccountsResult.Rejected rejectedAccounts =
        new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized());
    GetPostingResult.Found foundPosting =
        new GetPostingResult.Found(
            ContractFixtures.bookIdentity(), postingFact("posting-3", "idem-3"), Optional.empty());
    GetPostingResult.Rejected rejectedPosting =
        new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized());
    ListPostingsResult.Listed listedPostings =
        new ListPostingsResult.Listed(
            new ListPostingsQuery(Optional.empty(), null, null, 10, Optional.empty()),
            ContractFixtures.postingPage(
                Optional.empty(),
                EffectiveDateRange.unbounded(),
                List.of(postingFact("posting-4", "idem-4")),
                10,
                Optional.empty()));
    ListPostingsResult.Rejected rejectedPostings =
        new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized());
    AccountBalanceResult.Reported reportedBalance =
        new AccountBalanceResult.Reported(
            new AccountBalanceSnapshot(
                ContractFixtures.bookIdentity(),
                CASH_ACCOUNT,
                Optional.empty(),
                Optional.empty(),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(EUR_DEBIT_BALANCE)));
    AccountBalanceResult.Rejected rejectedBalance =
        new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), trialBalanceQuery.effectiveDateAsOf());
    assertEquals(PostingCoverage.ALL_POSTING_KINDS, trialBalanceQuery.postingCoverage());
    assertEquals(ComparativeSelection.none(), trialBalanceQuery.comparativeSelection());
    assertEquals(
        List.of(new TrialBalanceRow(CASH_ACCOUNT, EUR_DEBIT_BALANCE)), trialBalanceReport.rows());
    assertSame(trialBalanceReport, reportedTrialBalance.report());
    assertSame(trialBalanceReport, reportedTrialBalance.reported());
    assertNull(reportedTrialBalance.rejection());
    assertEquals(
        "query-book-not-initialized",
        BookQueryRejection.wireCode(rejectedTrialBalance.rejection()));
    assertEquals(
        Optional.of(LocalDate.parse("2026-04-01")), accountLedgerQuery.effectiveDateFrom());
    assertEquals(Optional.of(LocalDate.parse("2026-04-30")), accountLedgerQuery.effectiveDateTo());
    assertSame(accountLedgerReport, reportedAccountLedger.report());
    assertSame(accountLedgerReport, reportedAccountLedger.reported());
    assertNull(reportedAccountLedger.rejection());
    assertEquals(
        CASH_ACCOUNT.accountCode(),
        ((BookQueryRejection.UnknownAccount) rejectedAccountLedger.rejection()).accountCode());
    assertEquals(accountLedgerEntry, accountLedgerReport.entries().getFirst());
    assertEquals(LocalDate.parse("2026-04-01"), periodSummaryQuery.effectiveDateFrom());
    assertEquals(LocalDate.parse("2026-04-30"), periodSummaryQuery.effectiveDateTo());
    assertSame(periodSummaryReport, reportedPeriodSummary.report());
    assertSame(periodSummaryReport, reportedPeriodSummary.reported());
    assertNull(reportedPeriodSummary.rejection());
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
    assertSame(reportedBalance.snapshot(), reportedBalance.reported());
    assertNull(reportedBalance.rejection());
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
    assertThrows(
        NullPointerException.class,
        () ->
            new TrialBalanceQuery(
                nullOf(), PostingCoverage.ALL_POSTING_KINDS, ComparativeSelection.none()));
    assertEquals(
        "rows must not be null.",
        assertThrows(
                NullPointerException.class,
                () ->
                    new TrialBalanceReport(
                        ContractFixtures.bookIdentity(),
                        Optional.empty(),
                        Optional.empty(),
                        EffectiveDateRange.unbounded(),
                        PostingCoverage.ALL_POSTING_KINDS,
                        nullOf(),
                        List.of(),
                        false,
                        List.of(),
                        List.of(),
                        false))
            .getMessage());
    assertThrows(
        NullPointerException.class, () -> new TrialBalanceRow(nullOf(), EUR_DEBIT_BALANCE));
    assertThrows(NullPointerException.class, () -> new TrialBalanceResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new TrialBalanceResult.Rejected(nullOf()));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountLedgerQuery(
                nullOf(),
                EffectiveDateRange.unbounded(),
                PostingCoverage.ALL_POSTING_KINDS,
                ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
                Optional.empty()));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountLedgerQuery(
                CASH_ACCOUNT.accountCode(),
                nullOf(),
                PostingCoverage.ALL_POSTING_KINDS,
                ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
                Optional.empty()));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountLedgerReport(
                nullOf(),
                nullOf(),
                EffectiveDateRange.unbounded(),
                PostingCoverage.ALL_POSTING_KINDS,
                new AccountLedgerPagination(
                    ProtocolInteractionLimits.DEFAULT_PAGE_LIMIT,
                    Optional.empty(),
                    Optional.empty()),
                List.of(),
                List.of(),
                List.of()));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountLedgerEntry(
                nullOf(), EUR_DEBIT_BALANCE, EUR_DEBIT_BALANCE.netAmount(), BalanceSide.DEBIT));
    assertThrows(NullPointerException.class, () -> new AccountLedgerResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new AccountLedgerResult.Rejected(nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PeriodSummaryQuery(LocalDate.parse("2026-04-30"), LocalDate.parse("2026-04-01")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                ContractFixtures.bookIdentity(),
                LocalDate.parse("2026-04-30"),
                LocalDate.parse("2026-04-01"),
                PostingCoverage.ALL_POSTING_KINDS,
                0,
                0,
                0,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                ContractFixtures.bookIdentity(),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                PostingCoverage.ALL_POSTING_KINDS,
                -1,
                0,
                0,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                ContractFixtures.bookIdentity(),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                PostingCoverage.ALL_POSTING_KINDS,
                0,
                -1,
                0,
                List.of(),
                List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new PeriodSummaryReport(
                ContractFixtures.bookIdentity(),
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                PostingCoverage.ALL_POSTING_KINDS,
                0,
                0,
                -1,
                List.of(),
                List.of()));
    assertThrows(NullPointerException.class, () -> new PeriodCurrencySummary(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new PeriodAccountActivityRow(nullOf(), EUR_DEBIT_BALANCE));
    assertThrows(NullPointerException.class, () -> new PeriodSummaryResult.Reported(nullOf()));
    assertThrows(NullPointerException.class, () -> new PeriodSummaryResult.Rejected(nullOf()));
    AccountBalanceResult.Reported reportedBalance =
        new AccountBalanceResult.Reported(
            new AccountBalanceSnapshot(
                ContractFixtures.bookIdentity(),
                CASH_ACCOUNT,
                Optional.empty(),
                Optional.empty(),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(EUR_DEBIT_BALANCE)));
    assertThrows(
        NullPointerException.class, () -> reportedBalance.fold(nullOf(), ignored -> "rejected"));
    assertThrows(
        NullPointerException.class,
        () ->
            new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized())
                .fold(ignored -> "reported", nullOf()));
    AtomicInteger foldCounter = new AtomicInteger();
    ListAccountsResult.Listed listedAccounts =
        new ListAccountsResult.Listed(
            new ListAccountsQuery(50, Optional.empty()),
            ContractFixtures.accountPage(List.of(CASH_ACCOUNT), 50, Optional.empty()));
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
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
            "Wrong key", "Use the correct key file.", "--book-key-file");
    ContractDecision<String> accepted = ContractDecision.accepted("ok");
    ContractDecision<String> rejected = ContractDecision.rejected(withoutCause);
    assertEquals(19, descriptors.size());
    assertEquals("unknown-command", descriptors.getFirst().code());
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "interactive-prompt-unavailable".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "unsupported-output-selection".equals(descriptor.code())));
    assertTrue(
        descriptors.stream().anyMatch(descriptor -> "internal-defect".equals(descriptor.code())));
    assertTrue(
        descriptors.stream().anyMatch(descriptor -> "internal-error".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "managed-runtime-failure".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "storage-runtime-failure".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "pdf-export-failure".equals(descriptor.code())));
    assertEquals("invalid-page-cursor", ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code());
    assertEquals(1, ContractErrors.Descriptor.INVALID_PAGE_CURSOR.exitCode());
    assertEquals(2, ContractErrors.Descriptor.UNSUPPORTED_OUTPUT_SELECTION.exitCode());
    assertEquals(5, ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.exitCode());
    assertEquals(6, ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.exitCode());
    assertTrue(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED
            .description()
            .contains("verify"));
    assertSame(ContractErrors.Descriptor.INVALID_PAGE_CURSOR, withoutCause.descriptor());
    assertEquals("invalid-page-cursor", withoutCause.code());
    assertEquals("Retry without --cursor.", withoutCause.hint());
    assertEquals("--cursor", withoutCause.argument());
    assertEquals("Bad cursor", withoutCause.message());
    assertSame(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED, withCause.descriptor());
    assertEquals("protected-book-verification-failed", withCause.code());
    assertEquals("Use the correct key file.", withCause.hint());
    assertEquals("--book-key-file", withCause.argument());
    assertEquals("accepted:ok", accepted.fold(value -> "accepted:" + value, ignored -> "rejected"));
    assertEquals(
        "rejected:invalid-page-cursor",
        rejected.fold(ignored -> "accepted", failure -> "rejected:" + failure.code()));
    assertEquals("ok", accepted.requireAccepted());
    assertSame(withoutCause, rejected.requireRejected());
    ContractFailureException failureException =
        assertThrows(ContractFailureException.class, rejected::requireAccepted);
    assertEquals("Bad cursor", failureException.getMessage());
    assertSame(withoutCause, failureException.failure());
    assertEquals(
        "Expected a rejected contract decision.",
        assertThrows(IllegalStateException.class, accepted::requireRejected).getMessage());
    assertThrows(
        NullPointerException.class,
        () -> new ContractFailure(nullOf(), "message", null, null, null));
    assertThrows(NullPointerException.class, () -> ContractDecision.accepted(nullOf()));
    assertThrows(NullPointerException.class, () -> accepted.fold(nullOf(), ignored -> "rejected"));
    assertThrows(NullPointerException.class, () -> accepted.fold(value -> value, nullOf()));
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
    assertEquals("failed", SqliteCompileOptionsVerificationStatus.FAILED.wireValue());
    assertEquals("failed", SqliteCompileOptionsVerificationStatus.FAILED.toString());
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
                    Money.parse("EUR", "15.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "15.00")))),
        PostingLineage.direct(),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        ContractFixtures.accountingEvidence(idempotencyKey),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.PERSON,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("correlation-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }
}
