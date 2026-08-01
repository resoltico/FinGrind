package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationAccount;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationMovement;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationReport;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadReportPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingReadStatementPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationView;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingInventoryReadService;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.AttestationPostingCommitmentStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/** Application owner for report-oriented book reads. */
final class BookReportService {
  private final BookkeepingReadService bookkeepingReadService;
  private final AttestationPostingCommitmentStore attestationCommitmentStore;
  private final BookkeepingInventoryReadService bookkeepingInventoryReadService;
  private final BookLifecycleReportService lifecycleReportService;

  BookReportService(
      BookkeepingReadStore bookStore,
      AttestationPostingCommitmentStore attestationCommitmentStore,
      BookkeepingReadService bookkeepingReadService) {
    Objects.requireNonNull(bookStore, "bookStore");
    this.attestationCommitmentStore =
        Objects.requireNonNull(attestationCommitmentStore, "attestationCommitmentStore");
    this.bookkeepingReadService =
        Objects.requireNonNull(bookkeepingReadService, "bookkeepingReadService");
    this.bookkeepingInventoryReadService = new BookkeepingInventoryReadService(bookStore);
    this.lifecycleReportService =
        new BookLifecycleReportService(bookStore, this.bookkeepingReadService);
  }

  TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.trialBalance(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new TrialBalanceResult.Reported(
                BookkeepingReadReportPublishedLanguageTranslator.toPublished(value)),
        TrialBalanceResult.Rejected::new);
  }

  AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.accountLedger(BookReadQueryTranslator.fromPublished(query)),
        value -> {
          Map<PostingId, dev.erst.fingrind.contract.bookkeeping.AttestationCommit> commitments =
              AttestationPostingCommitmentProjection.resolve(
                  attestationCommitmentStore,
                  value.entries().stream()
                      .map(entry -> entry.posting().postingId())
                      .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
          return new AccountLedgerResult.Reported(
              BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                  bookkeepingReadService.requireInitializedBookIdentity(), value, commitments));
        },
        AccountLedgerResult.Rejected::new);
  }

  PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.periodSummary(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new PeriodSummaryResult.Reported(
                BookkeepingReadReportPublishedLanguageTranslator.toPublished(
                    bookkeepingReadService.requireInitializedBookIdentity(), value)),
        PeriodSummaryResult.Rejected::new);
  }

  FinancialPositionResult financialPosition(FinancialPositionQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.financialPosition(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new FinancialPositionResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        FinancialPositionResult.Rejected::new);
  }

  IncomeStatementResult incomeStatement(IncomeStatementQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.incomeStatement(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new IncomeStatementResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        IncomeStatementResult.Rejected::new);
  }

  InventoryValuationResult inventoryValuation(InventoryValuationQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingInventoryReadService.inventoryValuation(
            BookReadQueryTranslator.fromPublished(query)),
        values ->
            new InventoryValuationResult.Reported(
                new InventoryValuationReport(
                    bookkeepingReadService.requireInitializedBookIdentity(),
                    query.effectiveDateAsOf(),
                    query.includeMovements(),
                    values.stream()
                        .map(BookReportService::toPublishedInventoryValuation)
                        .toList())),
        InventoryValuationResult.Rejected::new);
  }

  AccrualCutoffScheduleResult accrualCutoffSchedule(AccrualCutoffScheduleQuery query) {
    return lifecycleReportService.accrualCutoffSchedule(query);
  }

  FixedAssetRegisterResult fixedAssetRegister(FixedAssetRegisterQuery query) {
    return lifecycleReportService.fixedAssetRegister(query);
  }

  FinancingRegisterResult financingRegister(FinancingRegisterQuery query) {
    return lifecycleReportService.financingRegister(query);
  }

  RealizedForeignExchangeRegisterResult realizedForeignExchangeRegister(
      RealizedForeignExchangeRegisterQuery query) {
    return lifecycleReportService.realizedForeignExchangeRegister(query);
  }

  LatvianPayrollRegisterResult latvianPayrollRegister(LatvianPayrollRegisterQuery query) {
    return lifecycleReportService.latvianPayrollRegister(query);
  }

  CashFlowStatementResult cashFlowStatement(CashFlowStatementQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.cashFlowStatement(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new CashFlowStatementResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        CashFlowStatementResult.Rejected::new);
  }

  ChangesInEquityResult changesInEquity(ChangesInEquityQuery query) {
    return BookReadOutcomeMapper.map(
        bookkeepingReadService.changesInEquity(BookReadQueryTranslator.fromPublished(query)),
        value ->
            new ChangesInEquityResult.Reported(
                BookkeepingReadStatementPublishedLanguageTranslator.toPublished(value)),
        ChangesInEquityResult.Rejected::new);
  }

  private static InventoryValuationAccount toPublishedInventoryValuation(
      InventoryValuationView valuation) {
    return new InventoryValuationAccount(
        valuation.account().accountCode(),
        valuation.account().accountName(),
        Objects.requireNonNull(valuation.account().unitOfMeasure(), "inventory unitOfMeasure"),
        valuation.pool().quantityOnHand(),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(valuation.pool().costPool()),
        valuation.roundedMovingAverageUnitCostProjection() == null
            ? null
            : dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                valuation.roundedMovingAverageUnitCostProjection()),
        valuation.movements().stream()
            .map(
                movement ->
                    new InventoryValuationMovement(
                        movement.postingId(),
                        movement.effectiveDate(),
                        movement.accountSequence(),
                        movement.kind(),
                        movement.quantityDelta(),
                        movement.costDeltaMinor()))
            .toList());
  }
}
