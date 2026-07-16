package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleRow;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollSettlementStatus;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterRow;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingAccrualCutoffReadService;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingFinancingReadService;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingFixedAssetReadService;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingLatvianPayrollReadService;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingRealizedForeignExchangeReadService;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import java.util.Comparator;
import java.util.Objects;

/** Projects reports that are owned by durable business lifecycle contexts. */
final class BookLifecycleReportService {
  private final BookkeepingReadService bookkeepingReadService;
  private final BookkeepingAccrualCutoffReadService accrualCutoffReadService;
  private final BookkeepingFixedAssetReadService fixedAssetReadService;
  private final BookkeepingFinancingReadService financingReadService;
  private final BookkeepingLatvianPayrollReadService latvianPayrollReadService;
  private final BookkeepingRealizedForeignExchangeReadService realizedForeignExchangeReadService;

  BookLifecycleReportService(
      BookkeepingReadStore bookStore, BookkeepingReadService bookkeepingReadService) {
    Objects.requireNonNull(bookStore, "bookStore");
    this.bookkeepingReadService =
        Objects.requireNonNull(bookkeepingReadService, "bookkeepingReadService");
    accrualCutoffReadService = new BookkeepingAccrualCutoffReadService(bookStore);
    fixedAssetReadService = new BookkeepingFixedAssetReadService(bookStore);
    financingReadService = new BookkeepingFinancingReadService(bookStore);
    latvianPayrollReadService = new BookkeepingLatvianPayrollReadService(bookStore);
    realizedForeignExchangeReadService =
        new BookkeepingRealizedForeignExchangeReadService(bookStore);
  }

  AccrualCutoffScheduleResult accrualCutoffSchedule(AccrualCutoffScheduleQuery query) {
    return BookReadOutcomeMapper.map(
        accrualCutoffReadService.schedule(query.effectiveDateAsOf()),
        values ->
            new AccrualCutoffScheduleResult.Reported(
                new AccrualCutoffScheduleReport(
                    bookkeepingReadService.requireInitializedBookIdentity(),
                    query.effectiveDateAsOf(),
                    values.stream()
                        .map(BookLifecycleReportService::toPublishedAccrualCutoff)
                        .toList())),
        AccrualCutoffScheduleResult.Rejected::new);
  }

  FixedAssetRegisterResult fixedAssetRegister(FixedAssetRegisterQuery query) {
    Objects.requireNonNull(query, "query");
    return BookReadOutcomeMapper.map(
        fixedAssetReadService.register(query.effectiveDateAsOf()),
        assets ->
            new FixedAssetRegisterResult.Reported(
                new FixedAssetRegisterReport(
                    bookkeepingReadService.requireInitializedBookIdentity(),
                    query.effectiveDateAsOf(),
                    assets.stream()
                        .map(BookLifecycleReportService::toPublishedFixedAsset)
                        .toList())),
        FixedAssetRegisterResult.Rejected::new);
  }

  FinancingRegisterResult financingRegister(FinancingRegisterQuery query) {
    Objects.requireNonNull(query, "query");
    return BookReadOutcomeMapper.map(
        financingReadService.register(),
        arrangements ->
            new FinancingRegisterResult.Reported(
                new FinancingRegisterReport(
                    bookkeepingReadService.requireInitializedBookIdentity(),
                    arrangements.stream()
                        .map(BookLifecycleReportService::toPublishedFinancing)
                        .toList())),
        FinancingRegisterResult.Rejected::new);
  }

  RealizedForeignExchangeRegisterResult realizedForeignExchangeRegister(
      RealizedForeignExchangeRegisterQuery query) {
    Objects.requireNonNull(query, "query");
    return BookReadOutcomeMapper.map(
        realizedForeignExchangeReadService.register(),
        obligations ->
            new RealizedForeignExchangeRegisterResult.Reported(
                new RealizedForeignExchangeRegisterReport(
                    bookkeepingReadService.requireInitializedBookIdentity(),
                    obligations.stream()
                        .map(BookLifecycleReportService::toPublishedRealizedForeignExchange)
                        .toList())),
        RealizedForeignExchangeRegisterResult.Rejected::new);
  }

  LatvianPayrollRegisterResult latvianPayrollRegister(LatvianPayrollRegisterQuery query) {
    Objects.requireNonNull(query, "query");
    return BookReadOutcomeMapper.map(
        latvianPayrollReadService.register(),
        facts ->
            new LatvianPayrollRegisterResult.Reported(
                new LatvianPayrollRegisterReport(
                    bookkeepingReadService.requireInitializedBookIdentity(),
                    facts.runs().stream()
                        .sorted(
                            Comparator.comparing(
                                    (dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord
                                            run) -> run.payrollMonth().wireValue())
                                .thenComparing(run -> run.payrollRunId().value()))
                        .map(run -> toPublishedPayrollRun(run, facts.settlements()))
                        .toList())),
        LatvianPayrollRegisterResult.Rejected::new);
  }

  private static FixedAssetRegisterRow toPublishedFixedAsset(FixedAssetRecord asset) {
    return new FixedAssetRegisterRow(
        asset.fixedAssetId(),
        asset.capitalizedOn(),
        asset.assetAccountCode(),
        asset.accumulatedDepreciationAccountCode(),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(asset.cost()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(asset.accumulatedDepreciation()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(asset.carryingAmount()),
        asset.depreciationSchedule(),
        asset.depreciationPeriodsApplied(),
        asset.latestLifecycleEffectiveDate(),
        asset.disposedOn());
  }

  private static FinancingRegisterRow toPublishedFinancing(FinancingArrangementRecord arrangement) {
    return new FinancingRegisterRow(
        arrangement.financingArrangementId(),
        arrangement.originatedOn(),
        arrangement.lifecycleHorizon(),
        arrangement.principalLiabilityAccountCode(),
        arrangement.interestPayableAccountCode(),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(arrangement.originalPrincipal()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(arrangement.principalRepaid()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            arrangement.outstandingPrincipal()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(arrangement.interestAccrued()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(arrangement.interestPaid()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            arrangement.outstandingInterest()));
  }

  private static RealizedForeignExchangeRegisterRow toPublishedRealizedForeignExchange(
      ForeignCurrencyObligationRecord obligation) {
    return new RealizedForeignExchangeRegisterRow(
        obligation.foreignCurrencyObligationId(),
        obligation.originatedOn(),
        obligation.lifecycleHorizon(),
        obligation.receivableAccountCode(),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(obligation.transactionAmount()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            obligation.initialFunctionalCarryingAmount()),
        obligation.settledOn(),
        obligation
            .functionalSettlementAmount()
            .map(dev.erst.fingrind.contract.bookkeeping.MonetaryAmount::of),
        obligation
            .realizedGainOrLossAmount()
            .map(dev.erst.fingrind.contract.bookkeeping.MonetaryAmount::of),
        obligation.realizedGain());
  }

  private static AccrualCutoffScheduleRow toPublishedAccrualCutoff(AccrualCutoffRecord cutoff) {
    return switch (cutoff) {
      case AccrualCutoffRecord.Prepayment prepayment ->
          new AccrualCutoffScheduleRow(
              prepayment.accrualCutoffId(),
              prepayment.kind(),
              prepayment.originatedOn(),
              prepayment.prepaymentAssetAccountCode(),
              prepayment.expenseAccountCode(),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(prepayment.originalAmount()),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(prepayment.appliedAmount()),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                  prepayment.remainingAmount()),
              java.util.Optional.of(prepayment.recognitionInterval().startDate()),
              java.util.Optional.of(prepayment.recognitionInterval().endDate()),
              prepayment.latestApplicationEffectiveDate());
      case AccrualCutoffRecord.DeferredRevenue deferredRevenue ->
          new AccrualCutoffScheduleRow(
              deferredRevenue.accrualCutoffId(),
              deferredRevenue.kind(),
              deferredRevenue.originatedOn(),
              deferredRevenue.deferredRevenueAccountCode(),
              deferredRevenue.revenueAccountCode(),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                  deferredRevenue.originalAmount()),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                  deferredRevenue.appliedAmount()),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                  deferredRevenue.remainingAmount()),
              java.util.Optional.of(deferredRevenue.recognitionInterval().startDate()),
              java.util.Optional.of(deferredRevenue.recognitionInterval().endDate()),
              deferredRevenue.latestApplicationEffectiveDate());
      case AccrualCutoffRecord.AccruedExpense accruedExpense ->
          new AccrualCutoffScheduleRow(
              accruedExpense.accrualCutoffId(),
              accruedExpense.kind(),
              accruedExpense.originatedOn(),
              accruedExpense.accruedExpenseLiabilityAccountCode(),
              accruedExpense.expenseAccountCode(),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                  accruedExpense.originalAmount()),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                  accruedExpense.appliedAmount()),
              dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                  accruedExpense.remainingAmount()),
              java.util.Optional.empty(),
              java.util.Optional.empty(),
              accruedExpense.latestApplicationEffectiveDate());
    };
  }

  private static LatvianPayrollRegisterRow toPublishedPayrollRun(
      dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord run,
      java.util.List<dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord>
          settlements) {
    return new LatvianPayrollRegisterRow(
        run.payrollRunId(),
        run.employeeReference(),
        run.payrollMonth(),
        run.originPostingId(),
        run.effectiveDate(),
        run.reversalPostingId(),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(run.calculation().grossWages()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            run.calculation().employeeSocialContribution()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            run.calculation().employerSocialContribution()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            run.calculation().monthlyNonTaxableMinimum()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            run.calculation().personalIncomeTax()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(run.calculation().netWages()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            run.calculation().totalEmployerCost()),
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
            run.calculation().stateRemittance()),
        settlements.stream()
            .filter(settlement -> settlement.payrollRunId().equals(run.payrollRunId()))
            .sorted(
                Comparator.comparing(
                    (dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord
                            settlement) -> settlement.settlementKind().wireValue()))
            .map(
                settlement ->
                    new LatvianPayrollSettlementStatus(
                        settlement.settlementKind(),
                        settlement.originPostingId(),
                        settlement.effectiveDate(),
                        settlement.reversalPostingId()))
            .toList());
  }
}
