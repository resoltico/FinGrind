package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffRecognitionInterval;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Direct branch coverage for reversal acceptance before durable commit. */
class ReversalAcceptancePolicyTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void rejectionFor_reportsMissingTargetAndNonNegatingCandidate() {
    PostingRequestModel missingTargetRequest =
        reversalRequest("idem-missing", "posting-missing", reversalJournalEntry());
    PostingRequestModel mismatchedReversalRequest =
        reversalRequest("idem-mismatch", "posting-1", originalJournalEntry());
    PostingValidationStore missingTargetStore = new PostingValidationStoreStub(Map.of());
    PostingValidationStore existingPostingStore =
        new PostingValidationStoreStub(
            Map.of(
                new PostingId("posting-1"), committedPosting("posting-1", originalJournalEntry())));

    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.ReversalTargetNotFound(
                new PostingId("posting-missing"))),
        ReversalAcceptancePolicy.rejectionFor(missingTargetRequest, missingTargetStore));
    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.ReversalDoesNotNegateTarget(
                new PostingId("posting-1"))),
        ReversalAcceptancePolicy.rejectionFor(mismatchedReversalRequest, existingPostingStore));
  }

  @Test
  void rejectionFor_rejectsTargetsThatAreAlreadyReversals() {
    PostingRequestModel reversalOfReversalRequest =
        reversalRequest("idem-reroll", "posting-reversal", originalJournalEntry());
    PostingValidationStore reversalTargetStore =
        new PostingValidationStoreStub(
            Map.of(
                new PostingId("posting-reversal"),
                reversalPosting("posting-reversal", "posting-original")));

    assertEquals(
        Optional.of(new ReversalTargetIsReversal(new PostingId("posting-reversal"))),
        ReversalAcceptancePolicy.rejectionFor(reversalOfReversalRequest, reversalTargetStore));
  }

  @Test
  void rejectionFor_preservesAccrualCutoffLifecycleAndOriginInvariants() {
    AccrualCutoffId cutoffId = new AccrualCutoffId("prepayment-2026-01");
    CommittedPosting origin = accrualCutoffOriginPosting("posting-prepayment", cutoffId);
    Map<PostingId, CommittedPosting> postings = Map.of(origin.postingId(), origin);

    PostingValidationStore horizonStore =
        new PostingValidationStoreStub(
            postings,
            Map.of(
                cutoffId,
                prepaymentCutoff(
                    cutoffId,
                    Money.zero(dev.erst.fingrind.core.CurrencyUnit.of("EUR")),
                    LocalDate.parse("2026-04-08"))));
    BookkeepingPostingRejection.EntrySemanticsViolations horizonRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            ReversalAcceptancePolicy.rejectionFor(
                    reversalRequest(
                        "idem-prepayment-horizon",
                        origin.postingId().value(),
                        reversalJournalEntry()),
                    horizonStore)
                .orElseThrow());
    assertEquals(
        "accrual-cutoff-reversal-precedes-horizon",
        horizonRejection.violations().getFirst().code());

    PostingValidationStore appliedStore =
        new PostingValidationStoreStub(
            postings,
            Map.of(
                cutoffId,
                prepaymentCutoff(
                    cutoffId, Money.parse("EUR", "1.00"), LocalDate.parse("2026-04-07"))));
    BookkeepingPostingRejection.EntrySemanticsViolations originRejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            ReversalAcceptancePolicy.rejectionFor(
                    reversalRequest(
                        "idem-prepayment-applied",
                        origin.postingId().value(),
                        reversalJournalEntry()),
                    appliedStore)
                .orElseThrow());
    assertEquals(
        "accrual-cutoff-origin-reversal-requires-zero-applications",
        originRejection.violations().getFirst().code());
  }

  @Test
  void rejectionFor_acceptsOrdinaryAndEveryAdmittedAccrualCutoffReversalTarget() {
    CommittedPosting ordinaryPosting = committedPosting("posting-ordinary", originalJournalEntry());
    assertEquals(
        Optional.empty(),
        ReversalAcceptancePolicy.rejectionFor(
            reversalRequest("idem-ordinary", "posting-ordinary", reversalJournalEntry()),
            new PostingValidationStoreStub(Map.of(ordinaryPosting.postingId(), ordinaryPosting))));

    List<AccrualCutoffReversalCase> reversalCases = accrualCutoffReversalCases();
    assertAccrualCutoffTargetAccepted(reversalCases.get(0));
    assertAccrualCutoffTargetAccepted(reversalCases.get(1));
    assertAccrualCutoffTargetAccepted(reversalCases.get(2));
    assertAccrualCutoffTargetAccepted(reversalCases.get(3));
    assertAccrualCutoffTargetAccepted(reversalCases.get(4));
  }

  @Test
  void rejectionFor_rejectsAnAlreadyReversedTargetAndMissingDurableCutoffAggregate() {
    CommittedPosting ordinaryPosting = committedPosting("posting-ordinary", originalJournalEntry());
    assertEquals(
        Optional.of(
            new BookkeepingPostingRejection.ReversalAlreadyExists(ordinaryPosting.postingId())),
        ReversalAcceptancePolicy.rejectionFor(
            reversalRequest("idem-duplicate", "posting-ordinary", reversalJournalEntry()),
            new PostingValidationStoreStub(
                Map.of(ordinaryPosting.postingId(), ordinaryPosting),
                Set.of(ordinaryPosting.postingId()))));

    AccrualCutoffId cutoffId = new AccrualCutoffId("missing-durable-cutoff");
    CommittedPosting prepaymentPosting =
        accrualCutoffPosting("posting-missing-cutoff", prepaymentEntry(cutoffId));
    assertThrows(
        IllegalStateException.class,
        () ->
            ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-missing-cutoff",
                    prepaymentPosting.postingId().value(),
                    reversalJournalEntry()),
                new PostingValidationStoreStub(
                    Map.of(prepaymentPosting.postingId(), prepaymentPosting))));
  }

  @Test
  void rejectionFor_preservesLatvianPayrollReversalOrderingAndSettlementClosure() {
    LatvianPayrollRunRecord run = payrollRun();
    CommittedPosting runPosting = payrollPosting("posting-payroll-run", monthlyPayroll());
    JournalEntry runReversal =
        negatedJournal(runPosting.journalEntry(), LocalDate.parse("2026-07-30"));

    assertThrows(
        IllegalStateException.class,
        () ->
            ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-missing-payroll-run", runPosting.postingId().value(), runReversal),
                new PayrollPostingValidationStore(
                    runPosting, Optional.empty(), Optional.empty(), Map.of())));
    assertEntrySemanticsCode(
        ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-early-payroll-run", runPosting.postingId().value(), runReversal),
                new PayrollPostingValidationStore(
                    runPosting, Optional.of(run), Optional.empty(), Map.of()))
            .orElseThrow(),
        "latvian-payroll-run-reversal-precedes-run");

    JournalEntry currentRunReversal =
        negatedJournal(runPosting.journalEntry(), run.effectiveDate());
    LatvianPayrollSettlementRecord activeStateRemittance =
        payrollSettlementRecord(
            LatvianPayrollSettlementKind.STATE_REMITTANCE,
            new PostingId("posting-active-state-remittance"));
    assertEntrySemanticsCode(
        ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-active-payroll-settlement",
                    runPosting.postingId().value(),
                    currentRunReversal),
                new PayrollPostingValidationStore(
                    runPosting,
                    Optional.of(run),
                    Optional.empty(),
                    Map.of(LatvianPayrollSettlementKind.STATE_REMITTANCE, activeStateRemittance)))
            .orElseThrow(),
        "latvian-payroll-run-reversal-requires-settlements-reversed");
    assertEquals(
        Optional.empty(),
        ReversalAcceptancePolicy.rejectionFor(
            reversalRequest(
                "idem-closed-payroll-settlements",
                runPosting.postingId().value(),
                currentRunReversal),
            new PayrollPostingValidationStore(
                runPosting, Optional.of(run), Optional.empty(), Map.of())));
  }

  @Test
  void rejectionFor_preservesEachLatvianPayrollSettlementReversalHorizon() {
    assertSettlementReversal(LatvianPayrollSettlementKind.NET_WAGES);
    assertSettlementReversal(LatvianPayrollSettlementKind.STATE_REMITTANCE);
  }

  private static void assertSettlementReversal(LatvianPayrollSettlementKind settlementKind) {
    LatvianPayrollBookkeepingEntryVariants settlement = payrollSettlementEntry(settlementKind);
    CommittedPosting settlementPosting =
        payrollPosting(
            "posting-" + settlementKind.wireValue().toLowerCase(Locale.ROOT), settlement);
    LatvianPayrollSettlementRecord settlementRecord =
        payrollSettlementRecord(settlementKind, settlementPosting.postingId());
    JournalEntry prematureReversal =
        negatedJournal(settlementPosting.journalEntry(), LocalDate.parse("2026-07-30"));
    JournalEntry admissibleReversal =
        negatedJournal(settlementPosting.journalEntry(), settlementRecord.effectiveDate());

    assertThrows(
        IllegalStateException.class,
        () ->
            ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-missing-" + settlementKind.wireValue(),
                    settlementPosting.postingId().value(),
                    prematureReversal),
                new PayrollPostingValidationStore(
                    settlementPosting, Optional.empty(), Optional.empty(), Map.of())));
    assertEntrySemanticsCode(
        ReversalAcceptancePolicy.rejectionFor(
                reversalRequest(
                    "idem-early-" + settlementKind.wireValue(),
                    settlementPosting.postingId().value(),
                    prematureReversal),
                new PayrollPostingValidationStore(
                    settlementPosting, Optional.empty(), Optional.of(settlementRecord), Map.of()))
            .orElseThrow(),
        "latvian-payroll-settlement-reversal-precedes-settlement");
    assertEquals(
        Optional.empty(),
        ReversalAcceptancePolicy.rejectionFor(
            reversalRequest(
                "idem-admitted-" + settlementKind.wireValue(),
                settlementPosting.postingId().value(),
                admissibleReversal),
            new PayrollPostingValidationStore(
                settlementPosting, Optional.empty(), Optional.of(settlementRecord), Map.of())));
  }

  private static void assertEntrySemanticsCode(
      BookkeepingPostingRejection rejection, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations semantics =
        assertInstanceOf(BookkeepingPostingRejection.EntrySemanticsViolations.class, rejection);
    assertEquals(expectedCode, semantics.violations().getFirst().code());
  }

  private static LatvianPayrollRunRecord payrollRun() {
    LatvianPayrollMonth payrollMonth = new LatvianPayrollMonth(YearMonth.of(2026, 7));
    LatvianMonthlyPayrollCalculation calculation =
        LatvianMonthlyPayroll2026.calculate(payrollMonth, Money.parse("EUR", "2000.00"));
    return new LatvianPayrollRunRecord(
        new LatvianPayrollRunId("payroll-2026-07-employee-1"),
        new LatvianPayrollEmployeeReference("employee-1"),
        payrollMonth,
        payrollMonth.value().atEndOfMonth(),
        new AccountCode("5000"),
        new AccountCode("5010"),
        new AccountCode("2200"),
        new AccountCode("2210"),
        new AccountCode("2220"),
        new AccountCode("2230"),
        calculation,
        new PostingId("posting-payroll-run"),
        Optional.empty());
  }

  private static LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll monthlyPayroll() {
    LatvianPayrollRunRecord run = payrollRun();
    return new LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll(
        run.effectiveDate(),
        run.payrollRunId(),
        run.employeeReference(),
        run.payrollMonth(),
        run.wageExpenseAccountCode(),
        run.employerSocialContributionExpenseAccountCode(),
        run.netWagesPayableAccountCode(),
        run.employeeSocialContributionPayableAccountCode(),
        run.employerSocialContributionPayableAccountCode(),
        run.personalIncomeTaxPayableAccountCode(),
        MonetaryAmount.of(run.calculation().grossWages()),
        run.calculation());
  }

  private static LatvianPayrollBookkeepingEntryVariants payrollSettlementEntry(
      LatvianPayrollSettlementKind settlementKind) {
    LatvianPayrollRunRecord run = payrollRun();
    ResolvedLatvianPayrollSettlement resolvedSettlement =
        new ResolvedLatvianPayrollSettlement(
            settlementKind,
            run.payrollRunId(),
            new AccountCode("1000"),
            run.netWagesPayableAccountCode(),
            run.employeeSocialContributionPayableAccountCode(),
            run.employerSocialContributionPayableAccountCode(),
            run.personalIncomeTaxPayableAccountCode(),
            run.calculation().netWages(),
            run.calculation().employeeSocialContribution(),
            run.calculation().employerSocialContribution(),
            run.calculation().personalIncomeTax());
    return switch (settlementKind) {
      case NET_WAGES ->
          new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
              run.effectiveDate(),
              run.payrollRunId(),
              resolvedSettlement.cashAccountCode(),
              resolvedSettlement);
      case STATE_REMITTANCE ->
          new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
              run.effectiveDate(),
              run.payrollRunId(),
              resolvedSettlement.cashAccountCode(),
              resolvedSettlement);
    };
  }

  private static LatvianPayrollSettlementRecord payrollSettlementRecord(
      LatvianPayrollSettlementKind settlementKind, PostingId originPostingId) {
    return new LatvianPayrollSettlementRecord(
        settlementKind,
        payrollRun().payrollRunId(),
        originPostingId,
        LocalDate.parse("2026-07-31"),
        new AccountCode("1000"),
        Optional.empty());
  }

  private static CommittedPosting payrollPosting(String postingId, BookkeepingEntry entry) {
    return new CommittedPosting(
        new PostingId(postingId),
        entry.journalEntry(),
        PostingLineageModel.direct(),
        entry.postingKind(),
        entry.postingOriginKind(),
        accountingEvidence("prior-" + postingId),
        new CommittedProvenance(
            requestProvenance("prior-" + postingId), DECLARED_AT, SourceChannel.CLI),
        entry,
        entry);
  }

  private static JournalEntry negatedJournal(JournalEntry original, LocalDate effectiveDate) {
    return new JournalEntry(
        effectiveDate,
        original.lines().stream()
            .map(
                line ->
                    new JournalLine(
                        line.accountCode(),
                        line.side() == JournalLine.EntrySide.DEBIT
                            ? JournalLine.EntrySide.CREDIT
                            : JournalLine.EntrySide.DEBIT,
                        line.amount()))
            .toList());
  }

  private static PostingRequestModel reversalRequest(
      String idempotencyKey, String priorPostingId, JournalEntry candidateJournalEntry) {
    ReversalReference reversalReference = new ReversalReference(new PostingId(priorPostingId));
    ReversalReason reversalReason = new ReversalReason("operator reversal");
    return new PostingCommand(
        PostingKind.STANDARD,
        PostingOriginKind.REVERSAL,
        candidateJournalEntry,
        PostingLineageModel.reversal(reversalReference, reversalReason),
        accountingEvidence(idempotencyKey),
        requestProvenance(idempotencyKey),
        SourceChannel.CLI);
  }

  private static CommittedPosting committedPosting(String postingId, JournalEntry journalEntry) {
    return new CommittedPosting(
        new PostingId(postingId),
        journalEntry,
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        PostingOriginKind.DIRECT_JOURNAL,
        accountingEvidence("prior-" + postingId),
        new CommittedProvenance(
            requestProvenance("prior-" + postingId),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static CommittedPosting reversalPosting(String postingId, String priorPostingId) {
    return new CommittedPosting(
        new PostingId(postingId),
        reversalJournalEntry(),
        PostingLineageModel.reversal(
            new ReversalReference(new PostingId(priorPostingId)),
            new ReversalReason("historical full reversal")),
        PostingKind.STANDARD,
        PostingOriginKind.REVERSAL,
        accountingEvidence("prior-" + postingId),
        new CommittedProvenance(
            requestProvenance("prior-" + postingId),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static CommittedPosting accrualCutoffOriginPosting(
      String postingId, AccrualCutoffId accrualCutoffId) {
    AccrualCutoffBookkeepingEntryVariants.Prepayment prepayment =
        new AccrualCutoffBookkeepingEntryVariants.Prepayment(
            LocalDate.parse("2026-04-07"),
            accrualCutoffId,
            new AccountCode("1000"),
            new AccountCode("3000"),
            new AccountCode("2000"),
            MonetaryAmount.of(Money.parse("EUR", "10.00")),
            new AccrualCutoffRecognitionInterval(
                LocalDate.parse("2026-04-07"), LocalDate.parse("2026-05-31")));
    return new CommittedPosting(
        new PostingId(postingId),
        originalJournalEntry(),
        PostingLineageModel.direct(),
        PostingKind.STANDARD,
        PostingOriginKind.PREPAYMENT,
        accountingEvidence("prior-" + postingId),
        new CommittedProvenance(
            requestProvenance("prior-" + postingId), DECLARED_AT, SourceChannel.CLI),
        prepayment,
        null);
  }

  private static List<AccrualCutoffReversalCase> accrualCutoffReversalCases() {
    AccrualCutoffId prepaymentId = new AccrualCutoffId("prepayment-2026-04");
    AccrualCutoffId deferredRevenueId = new AccrualCutoffId("deferred-revenue-2026-04");
    AccrualCutoffId accruedExpenseId = new AccrualCutoffId("accrued-expense-2026-04");
    return List.of(
        new AccrualCutoffReversalCase(
            "prepayment-origin",
            accrualCutoffPosting("posting-prepayment", prepaymentEntry(prepaymentId)),
            prepaymentCutoff(
                prepaymentId, Money.parse("EUR", "0.00"), LocalDate.parse("2026-04-07"))),
        new AccrualCutoffReversalCase(
            "deferred-revenue-origin",
            accrualCutoffPosting(
                "posting-deferred-revenue", deferredRevenueEntry(deferredRevenueId)),
            deferredRevenueCutoff(deferredRevenueId)),
        new AccrualCutoffReversalCase(
            "accrued-expense-origin",
            accrualCutoffPosting("posting-accrued-expense", accruedExpenseEntry(accruedExpenseId)),
            accruedExpenseCutoff(accruedExpenseId)),
        new AccrualCutoffReversalCase(
            "recognition-application",
            accrualCutoffPosting("posting-recognition", recognitionEntry(prepaymentId)),
            prepaymentCutoff(
                prepaymentId, Money.parse("EUR", "0.00"), LocalDate.parse("2026-04-07"))),
        new AccrualCutoffReversalCase(
            "settlement-application",
            accrualCutoffPosting("posting-settlement", settlementEntry(accruedExpenseId)),
            accruedExpenseCutoff(accruedExpenseId)));
  }

  private static void assertAccrualCutoffTargetAccepted(AccrualCutoffReversalCase reversalCase) {
    assertEquals(
        Optional.empty(),
        ReversalAcceptancePolicy.rejectionFor(
            reversalRequest(
                "idem-" + reversalCase.name(),
                reversalCase.posting().postingId().value(),
                reversalJournalEntry()),
            new PostingValidationStoreStub(
                Map.of(reversalCase.posting().postingId(), reversalCase.posting()),
                Map.of(reversalCase.cutoff().accrualCutoffId(), reversalCase.cutoff()))),
        reversalCase.name());
  }

  private static CommittedPosting accrualCutoffPosting(String postingId, BookkeepingEntry entry) {
    return new CommittedPosting(
        new PostingId(postingId),
        originalJournalEntry(),
        PostingLineageModel.direct(),
        entry.postingKind(),
        entry.postingOriginKind(),
        accountingEvidence("prior-" + postingId),
        new CommittedProvenance(
            requestProvenance("prior-" + postingId), DECLARED_AT, SourceChannel.CLI),
        entry,
        null);
  }

  private static AccrualCutoffBookkeepingEntryVariants.Prepayment prepaymentEntry(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.Prepayment(
        LocalDate.parse("2026-04-07"),
        accrualCutoffId,
        new AccountCode("1000"),
        new AccountCode("3000"),
        new AccountCode("2000"),
        MonetaryAmount.of(Money.parse("EUR", "10.00")),
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-05-31")));
  }

  private static AccrualCutoffBookkeepingEntryVariants.DeferredRevenue deferredRevenueEntry(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.DeferredRevenue(
        LocalDate.parse("2026-04-07"),
        accrualCutoffId,
        new AccountCode("2000"),
        new AccountCode("2100"),
        new AccountCode("4000"),
        MonetaryAmount.of(Money.parse("EUR", "10.00")),
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-05-31")));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpense accruedExpenseEntry(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpense(
        LocalDate.parse("2026-04-07"),
        accrualCutoffId,
        new AccountCode("5000"),
        new AccountCode("2100"),
        MonetaryAmount.of(Money.parse("EUR", "10.00")));
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition recognitionEntry(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.AccrualCutoffRecognition(
        LocalDate.parse("2026-04-07"),
        accrualCutoffId,
        MonetaryAmount.of(Money.parse("EUR", "10.00")),
        null);
  }

  private static AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement settlementEntry(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffBookkeepingEntryVariants.AccruedExpenseSettlement(
        LocalDate.parse("2026-04-07"),
        accrualCutoffId,
        new AccountCode("1000"),
        MonetaryAmount.of(Money.parse("EUR", "10.00")),
        null);
  }

  private static AccrualCutoffRecord.Prepayment prepaymentCutoff(
      AccrualCutoffId accrualCutoffId,
      Money appliedAmount,
      LocalDate latestApplicationEffectiveDate) {
    return new AccrualCutoffRecord.Prepayment(
        accrualCutoffId,
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("3000"),
        Money.parse("EUR", "10.00"),
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-05-31")),
        appliedAmount,
        Optional.of(latestApplicationEffectiveDate));
  }

  private static AccrualCutoffRecord.DeferredRevenue deferredRevenueCutoff(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffRecord.DeferredRevenue(
        accrualCutoffId,
        LocalDate.parse("2026-04-07"),
        new AccountCode("2100"),
        new AccountCode("4000"),
        Money.parse("EUR", "10.00"),
        new AccrualCutoffRecognitionInterval(
            LocalDate.parse("2026-04-07"), LocalDate.parse("2026-05-31")),
        Money.parse("EUR", "0.00"),
        Optional.of(LocalDate.parse("2026-04-07")));
  }

  private static AccrualCutoffRecord.AccruedExpense accruedExpenseCutoff(
      AccrualCutoffId accrualCutoffId) {
    return new AccrualCutoffRecord.AccruedExpense(
        accrualCutoffId,
        LocalDate.parse("2026-04-07"),
        new AccountCode("2100"),
        new AccountCode("5000"),
        Money.parse("EUR", "10.00"),
        Money.parse("EUR", "0.00"),
        Optional.of(LocalDate.parse("2026-04-07")));
  }

  private static RequestProvenance requestProvenance(String idempotencyKey) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")));
  }

  private static JournalEntry originalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "10.00"))));
  }

  private static JournalEntry reversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"), JournalLine.EntrySide.CREDIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.DEBIT,
                Money.parse("EUR", "10.00"))));
  }

  /** Minimal validation-store stub for targeted reversal-acceptance branch coverage. */
  private static class PostingValidationStoreStub implements PostingValidationStore {
    private final Map<PostingId, CommittedPosting> postingsById;
    private final Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById;
    private final Set<PostingId> reversedPostingIds;

    private PostingValidationStoreStub(Map<PostingId, CommittedPosting> postingsById) {
      this(postingsById, Map.of(), Set.of());
    }

    private PostingValidationStoreStub(
        Map<PostingId, CommittedPosting> postingsById, Set<PostingId> reversedPostingIds) {
      this(postingsById, Map.of(), reversedPostingIds);
    }

    private PostingValidationStoreStub(
        Map<PostingId, CommittedPosting> postingsById,
        Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById) {
      this(postingsById, cutoffsById, Set.of());
    }

    private PostingValidationStoreStub(
        Map<PostingId, CommittedPosting> postingsById,
        Map<AccrualCutoffId, AccrualCutoffRecord> cutoffsById,
        Set<PostingId> reversedPostingIds) {
      this.postingsById = postingsById;
      this.cutoffsById = cutoffsById;
      this.reversedPostingIds = reversedPostingIds;
    }

    @Override
    public BookLifecycleInspection inspectBook() {
      return new BookLifecycleInspection.Initialized(1001, 1, 1, DECLARED_AT, bookIdentity());
    }

    @Override
    public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
      return Optional.empty();
    }

    @Override
    public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
      return Map.of();
    }

    @Override
    public Optional<dev.erst.fingrind.contract.tax.DeclaredTaxRegistration> findTaxRegistration(
        dev.erst.fingrind.contract.tax.TaxRegistrationId taxRegistrationId) {
      return Optional.empty();
    }

    @Override
    public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
      return Optional.empty();
    }

    @Override
    public Optional<CommittedPosting> findPosting(PostingId postingId) {
      return Optional.ofNullable(postingsById.get(postingId));
    }

    @Override
    public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
      return reversedPostingIds.contains(priorPostingId)
          ? Optional.ofNullable(postingsById.get(priorPostingId))
          : Optional.empty();
    }

    @Override
    public Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
      return Optional.ofNullable(cutoffsById.get(accrualCutoffId));
    }

    @Override
    public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
      return List.of();
    }

    @Override
    public Optional<LocalDate> earliestPostingEffectiveDate() {
      return Optional.empty();
    }

    @Override
    public Optional<LocalDate> transferredThroughEffectiveDate() {
      return Optional.empty();
    }
  }

  /** Validation store carrying the payroll aggregates required by payroll reversal policy. */
  private static final class PayrollPostingValidationStore extends PostingValidationStoreStub {
    private final Optional<LatvianPayrollRunRecord> run;
    private final Optional<LatvianPayrollSettlementRecord> settlement;
    private final Map<LatvianPayrollSettlementKind, LatvianPayrollSettlementRecord>
        activeSettlements;

    private PayrollPostingValidationStore(
        CommittedPosting posting,
        Optional<LatvianPayrollRunRecord> run,
        Optional<LatvianPayrollSettlementRecord> settlement,
        Map<LatvianPayrollSettlementKind, LatvianPayrollSettlementRecord> activeSettlements) {
      super(Map.of(posting.postingId(), posting));
      this.run = run;
      this.settlement = settlement;
      this.activeSettlements = activeSettlements;
    }

    @Override
    public Optional<LatvianPayrollSettlementRecord> findLatvianPayrollSettlementByPosting(
        PostingId originPostingId) {
      return settlement.filter(candidate -> candidate.originPostingId().equals(originPostingId));
    }

    @Override
    public Optional<LatvianPayrollRunRecord> findLatvianPayrollRunByOriginPosting(
        PostingId originPostingId) {
      return run.filter(candidate -> candidate.originPostingId().equals(originPostingId));
    }

    @Override
    public Optional<LatvianPayrollSettlementRecord> findActiveLatvianPayrollSettlement(
        LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
      @Nullable LatvianPayrollSettlementRecord candidate = activeSettlements.get(settlementKind);
      if (candidate == null || !candidate.payrollRunId().equals(payrollRunId)) {
        return Optional.empty();
      }
      return Optional.of(candidate);
    }
  }

  private record AccrualCutoffReversalCase(
      String name, CommittedPosting posting, AccrualCutoffRecord cutoff) {}
}
