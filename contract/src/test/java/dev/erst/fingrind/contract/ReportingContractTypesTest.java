package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import dev.erst.fingrind.contract.runtime.CommitGuarantee;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureDetails;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.contract.runtime.ContractFailurePaths;
import dev.erst.fingrind.contract.runtime.ErrorDescriptor;
import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.InitializationRequirement;
import dev.erst.fingrind.contract.runtime.SqliteCompileOptionsVerificationStatus;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.ArtifactPublicationResult;
import dev.erst.fingrind.core.ArtifactPublicationRetention;
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
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
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
            BalanceSide.DEBIT,
            null);
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
            ContractFixtures.bookIdentity(),
            postingFact("posting-3", "idem-3"),
            Optional.empty(),
            Optional.empty());
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
                nullOf(),
                EUR_DEBIT_BALANCE,
                EUR_DEBIT_BALANCE.netAmount(),
                BalanceSide.DEBIT,
                null));
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
  void contractErrorDescriptors_exposeCanonicalDeterministicFailureMetadata() {
    List<ErrorDescriptor> descriptors = ContractErrors.descriptors();
    assertEquals(35, descriptors.size());
    assertEquals("unknown-command", descriptors.getFirst().code());
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "interactive-prompt-unavailable".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "unsupported-output-selection".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "custodian-not-supported".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(
                descriptor -> "attestation-credentials-not-allowed".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(
                descriptor -> "attestation-review-window-exceeds-head".equals(descriptor.code())));
    assertTrue(
        descriptors.stream().anyMatch(descriptor -> "internal-defect".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .noneMatch(
                descriptor ->
                    "protected-book-pair-publication-uncertain".equals(descriptor.code())));
    assertTrue(
        descriptors.stream().anyMatch(descriptor -> "internal-error".equals(descriptor.code())));
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "managed-runtime-failure".equals(descriptor.code())));
    ErrorDescriptor invalidArtifactOutputDirectoryDescriptor =
        descriptors.stream()
            .filter(descriptor -> "invalid-artifact-output-directory".equals(descriptor.code()))
            .findFirst()
            .orElseThrow();
    assertEquals(FailureCategory.PRECONDITION, invalidArtifactOutputDirectoryDescriptor.category());
    assertEquals(6, invalidArtifactOutputDirectoryDescriptor.exitCode());
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "storage-runtime-failure".equals(descriptor.code())));
    ErrorDescriptor artifactPublicationUncertainDescriptor =
        descriptors.stream()
            .filter(
                descriptor -> "artifact-publication-durability-uncertain".equals(descriptor.code()))
            .findFirst()
            .orElseThrow();
    assertEquals(FailureCategory.PRECONDITION, artifactPublicationUncertainDescriptor.category());
    assertEquals(4, artifactPublicationUncertainDescriptor.exitCode());
    assertEquals(
        List.of("publishedArtifact"),
        artifactPublicationUncertainDescriptor.detailFields().stream()
            .map(FieldDescriptor::name)
            .toList());
    ErrorDescriptor incompleteTransactionDescriptor =
        descriptors.stream()
            .filter(descriptor -> "publication-transaction-incomplete".equals(descriptor.code()))
            .findFirst()
            .orElseThrow();
    assertEquals(FailureCategory.PRECONDITION, incompleteTransactionDescriptor.category());
    assertEquals(4, incompleteTransactionDescriptor.exitCode());
    assertEquals(
        List.of("candidateArtifact", "publicationTransaction"),
        incompleteTransactionDescriptor.detailFields().stream()
            .map(FieldDescriptor::name)
            .toList());
    ErrorDescriptor artifactPublicationOutcomeDescriptor =
        descriptors.stream()
            .filter(
                descriptor -> "artifact-publication-outcome-uncertain".equals(descriptor.code()))
            .findFirst()
            .orElseThrow();
    assertEquals(FailureCategory.PRECONDITION, artifactPublicationOutcomeDescriptor.category());
    assertEquals(4, artifactPublicationOutcomeDescriptor.exitCode());
    assertEquals(
        List.of("candidateArtifact", "retainedStage"),
        artifactPublicationOutcomeDescriptor.detailFields().stream()
            .map(FieldDescriptor::name)
            .toList());
    assertTrue(
        descriptors.stream()
            .anyMatch(
                descriptor -> "protected-book-verification-failed".equals(descriptor.code())));
    ErrorDescriptor unsupportedFormatDescriptor =
        descriptors.stream()
            .filter(descriptor -> "unsupported-book-format-version".equals(descriptor.code()))
            .findFirst()
            .orElseThrow();
    assertEquals(FailureCategory.PRECONDITION, unsupportedFormatDescriptor.category());
    assertEquals(6, unsupportedFormatDescriptor.exitCode());
    assertEquals(
        List.of("detectedBookFormatVersion", "supportedBookFormatVersion"),
        unsupportedFormatDescriptor.detailFields().stream().map(FieldDescriptor::name).toList());
    assertTrue(
        descriptors.stream()
            .anyMatch(descriptor -> "pdf-export-failure".equals(descriptor.code())));
    assertEquals("invalid-page-cursor", ContractErrors.Descriptor.INVALID_PAGE_CURSOR.code());
    assertEquals(1, ContractErrors.Descriptor.INVALID_PAGE_CURSOR.exitCode());
    assertEquals(2, ContractErrors.Descriptor.UNSUPPORTED_OUTPUT_SELECTION.exitCode());
    assertEquals(1, ContractErrors.Descriptor.ATTESTATION_CREDENTIALS_NOT_ALLOWED.exitCode());
    assertEquals(5, ContractErrors.Descriptor.INTERACTIVE_PROMPT_UNAVAILABLE.exitCode());
    assertEquals(6, ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.exitCode());
    assertTrue(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED
            .description()
            .contains("verify"));
  }

  @Test
  void contractFailureFactories_exposeCanonicalFailureFacts() {
    ContractFailure withoutCause =
        ContractErrors.Descriptor.INVALID_PAGE_CURSOR.failure(
            "Bad cursor", "Retry without --cursor.", "--cursor");
    ContractFailure withCause =
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.failure(
            "Wrong key", "Use the correct key file.", "--book-key-file");
    ContractFailure protectedBookVerificationFailure =
        ContractErrors.protectedBookVerificationFailure();
    ContractFailure unsupportedBookFormatVersionFailure =
        ContractErrors.unsupportedBookFormatVersionFailure(7, 8);

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
    assertSame(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED,
        protectedBookVerificationFailure.descriptor());
    assertTrue(protectedBookVerificationFailure.message().contains("authenticate and verify"));
    assertNull(protectedBookVerificationFailure.argument());
    assertEquals("unsupported-book-format-version", unsupportedBookFormatVersionFailure.code());
    assertEquals("--book-file", unsupportedBookFormatVersionFailure.argument());
    var unsupportedFormatDetails =
        assertInstanceOf(
            ContractFailureDetails.UnsupportedBookFormatVersion.class,
            unsupportedBookFormatVersionFailure.details());
    assertEquals(7, unsupportedFormatDetails.detectedBookFormatVersion());
    assertEquals(8, unsupportedFormatDetails.supportedBookFormatVersion());
  }

  @Test
  void artifactPublicationFailureFactories_exposeCanonicalDetailsAndPaths() {
    Path publishedArtifactPath = Path.of("private-output", "receipt.fgar");
    Path residualStagePath = Path.of("private-output", ".fingrind-receipt-stage.tmp");
    ArtifactPublicationRetention retention = new ArtifactPublicationRetention(residualStagePath);
    ContractFailure artifactPublicationUncertainFailure =
        ContractErrors.artifactPublicationDurabilityUncertainFailure(
            new ArtifactPublicationResult(publishedArtifactPath, retention), "--receipt-file");
    assertEquals(
        "artifact-publication-durability-uncertain", artifactPublicationUncertainFailure.code());
    assertEquals("--receipt-file", artifactPublicationUncertainFailure.argument());
    var artifactPublicationUncertainDetails =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationDurabilityUncertain.class,
            artifactPublicationUncertainFailure.details());
    assertEquals(
        publishedArtifactPath.toAbsolutePath().normalize(),
        artifactPublicationUncertainDetails.publication().publishedArtifactPath());
    assertEquals(
        residualStagePath.toAbsolutePath().normalize(),
        artifactPublicationUncertainDetails.publication().retention().retainedStagePath());
    assertEquals(retention, artifactPublicationUncertainFailure.retainedStage());

    ContractFailure artifactPublicationOutcomeFailure =
        ContractErrors.artifactPublicationOutcomeUncertainFailure(
            publishedArtifactPath, retention, "--receipt-file");
    assertEquals(
        "artifact-publication-outcome-uncertain", artifactPublicationOutcomeFailure.code());
    assertEquals("--receipt-file", artifactPublicationOutcomeFailure.argument());
    var artifactPublicationOutcomeDetails =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class,
            artifactPublicationOutcomeFailure.details());
    assertEquals(
        publishedArtifactPath.toAbsolutePath().normalize(),
        artifactPublicationOutcomeDetails.candidateArtifactPath());
    assertEquals(
        residualStagePath.toAbsolutePath().normalize(),
        Objects.requireNonNull(artifactPublicationOutcomeDetails.retainedStage(), "outcome stage")
            .retainedStagePath());
    assertEquals(retention, artifactPublicationOutcomeFailure.retainedStage());
    ContractFailurePaths outcomePaths =
        Objects.requireNonNull(artifactPublicationOutcomeFailure.paths(), "outcome paths");
    assertEquals(publishedArtifactPath.toAbsolutePath().normalize(), outcomePaths.path());
    assertEquals(
        List.of(residualStagePath.toAbsolutePath().normalize()), outcomePaths.relatedPaths());

    ContractFailure cleanArtifactPublicationOutcomeFailure =
        ContractErrors.artifactPublicationOutcomeUncertainFailure(
            publishedArtifactPath, null, "--receipt-file");
    var cleanArtifactPublicationOutcomeDetails =
        assertInstanceOf(
            ContractFailureDetails.ArtifactPublicationOutcomeUncertain.class,
            cleanArtifactPublicationOutcomeFailure.details());
    assertNull(cleanArtifactPublicationOutcomeDetails.retainedStage());
    assertNull(cleanArtifactPublicationOutcomeFailure.retainedStage());
    ContractFailurePaths cleanOutcomePaths =
        Objects.requireNonNull(
            cleanArtifactPublicationOutcomeFailure.paths(), "clean outcome paths");
    assertEquals(publishedArtifactPath.toAbsolutePath().normalize(), cleanOutcomePaths.path());
    assertEquals(List.of(), cleanOutcomePaths.relatedPaths());
  }

  @Test
  void contractDecisionsAndFailures_enforceDeclaredSemantics() {
    ContractFailure withoutCause =
        ContractErrors.Descriptor.INVALID_PAGE_CURSOR.failure(
            "Bad cursor", "Retry without --cursor.", "--cursor");
    ContractDecision<String> accepted = ContractDecision.accepted("ok");
    ContractDecision<String> rejected = ContractDecision.rejected(withoutCause);

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
        () -> new ContractFailure(nullOf(), "message", null, null, null, null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            ContractErrors.Descriptor.UNSUPPORTED_BOOK_FORMAT_VERSION.failure(
                "Missing format facts", null, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailure(
                ContractErrors.Descriptor.INVALID_PAGE_CURSOR,
                "Unexpected format facts",
                null,
                null,
                null,
                new ContractFailureDetails.UnsupportedBookFormatVersion(7, 8),
                null));
    assertEquals(
        Path.of("clean-artifact").toAbsolutePath().normalize(),
        new ContractFailureDetails.ArtifactPublicationDurabilityUncertain(
                new ArtifactPublicationResult(
                    Path.of("clean-artifact"),
                    new ArtifactPublicationRetention(Path.of(".clean-artifact-stage"))))
            .publication()
            .publishedArtifactPath());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new ContractFailure(
                ContractErrors.Descriptor.ARTIFACT_PUBLICATION_OUTCOME_UNCERTAIN,
                "Missing outcome facts",
                null,
                null,
                null,
                null,
                null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractFailureDetails.UnsupportedBookFormatVersion(-1, 8));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractFailureDetails.UnsupportedBookFormatVersion(54, 0));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ContractFailureDetails.UnsupportedBookFormatVersion(8, 8));
    assertThrows(NullPointerException.class, () -> ContractDecision.accepted(nullOf()));
    assertThrows(NullPointerException.class, () -> accepted.fold(nullOf(), ignored -> "rejected"));
    assertThrows(NullPointerException.class, () -> accepted.fold(value -> value, nullOf()));
  }

  @Test
  void descriptorEnums_publishStableWireValuesAndLegacyBooleanMappings() {
    assertEquals("requires-open-book", InitializationRequirement.REQUIRES_OPEN_BOOK.wireValue());
    assertEquals("requires-open-book", InitializationRequirement.REQUIRES_OPEN_BOOK.toString());
    assertEquals("guaranteed", CommitGuarantee.GUARANTEED.wireValue());
    assertEquals("guaranteed", CommitGuarantee.GUARANTEED.toString());
    assertEquals("not-guaranteed", CommitGuarantee.NOT_GUARANTEED.wireValue());
    assertEquals("not-guaranteed", CommitGuarantee.NOT_GUARANTEED.toString());
    assertEquals(CommitGuarantee.GUARANTEED, CommitGuarantee.fromGuaranteed(true));
    assertEquals(CommitGuarantee.NOT_GUARANTEED, CommitGuarantee.fromGuaranteed(false));
    assertEquals("verified", SqliteCompileOptionsVerificationStatus.VERIFIED.wireValue());
    assertEquals("verified", SqliteCompileOptionsVerificationStatus.VERIFIED.toString());
    assertEquals("failed", SqliteCompileOptionsVerificationStatus.FAILED.wireValue());
    assertEquals("failed", SqliteCompileOptionsVerificationStatus.FAILED.toString());
    assertEquals("not-verified", SqliteCompileOptionsVerificationStatus.NOT_VERIFIED.wireValue());
    assertEquals("not-verified", SqliteCompileOptionsVerificationStatus.NOT_VERIFIED.toString());
  }

  private static PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(
            java.util
                .UUID
                .nameUUIDFromBytes(
                    ("fingrind-test-postingid:" + postingId)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString()),
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
                new CommandId("20aea0ba-3b2e-3428-af5b-f9ee3094522c"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("correlation-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }
}
