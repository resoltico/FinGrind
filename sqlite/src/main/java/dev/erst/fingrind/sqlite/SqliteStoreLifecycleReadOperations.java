package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import dev.erst.fingrind.executor.bookkeeping.FixedAssetRecord;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollSettlementRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Routes durable lifecycle context reads to their own query owners. */
final class SqliteStoreLifecycleReadOperations {
  private final AccrualCutoffReadOperations accrualCutoff;
  private final FixedAssetReadOperations fixedAssets;
  private final FinancingReadOperations financing;
  private final RealizedForeignExchangeReadOperations realizedForeignExchange;
  private final LatvianPayrollReadOperations latvianPayroll;

  SqliteStoreLifecycleReadOperations(
      SqliteStoreQueryOperations queryOperations, SqliteStoreLifecycle lifecycle) {
    SqliteStoreLifecycleQueryOperations lifecycleQueries =
        new SqliteStoreLifecycleQueryOperations(
            Objects.requireNonNull(queryOperations, "queryOperations"));
    SqliteStoreLatvianPayrollQueryOperations payrollQueries =
        new SqliteStoreLatvianPayrollQueryOperations(
            Objects.requireNonNull(lifecycle, "lifecycle"));
    accrualCutoff = new AccrualCutoffReadOperations(lifecycleQueries);
    fixedAssets = new FixedAssetReadOperations(lifecycleQueries);
    financing = new FinancingReadOperations(lifecycleQueries);
    realizedForeignExchange = new RealizedForeignExchangeReadOperations(lifecycleQueries);
    latvianPayroll = new LatvianPayrollReadOperations(payrollQueries);
  }

  AccrualCutoffReadOperations accrualCutoff() {
    return accrualCutoff;
  }

  FixedAssetReadOperations fixedAssets() {
    return fixedAssets;
  }

  FinancingReadOperations financing() {
    return financing;
  }

  RealizedForeignExchangeReadOperations realizedForeignExchange() {
    return realizedForeignExchange;
  }

  LatvianPayrollReadOperations latvianPayroll() {
    return latvianPayroll;
  }

  /** Reads durable aggregate state that the Accrual Cut-off context owns. */
  static final class AccrualCutoffReadOperations {
    private final SqliteStoreLifecycleQueryOperations queryOperations;

    private AccrualCutoffReadOperations(SqliteStoreLifecycleQueryOperations queryOperations) {
      this.queryOperations = queryOperations;
    }

    Optional<AccrualCutoffRecord> findAccrualCutoff(AccrualCutoffId accrualCutoffId) {
      return queryOperations.findAccrualCutoff(accrualCutoffId);
    }

    List<AccrualCutoffRecord> accrualCutoffs(Optional<LocalDate> effectiveDateAsOf) {
      return queryOperations.accrualCutoffs(effectiveDateAsOf);
    }
  }

  /** Reads durable aggregate state that the Fixed Assets context owns. */
  static final class FixedAssetReadOperations {
    private final SqliteStoreLifecycleQueryOperations queryOperations;

    private FixedAssetReadOperations(SqliteStoreLifecycleQueryOperations queryOperations) {
      this.queryOperations = queryOperations;
    }

    List<FixedAssetRecord> fixedAssets(Optional<LocalDate> effectiveDateAsOf) {
      return queryOperations.fixedAssets(effectiveDateAsOf);
    }

    boolean hasFixedAsset(FixedAssetId fixedAssetId) {
      return queryOperations.hasFixedAsset(fixedAssetId);
    }
  }

  /** Reads durable Financing aggregates. */
  static final class FinancingReadOperations {
    private final SqliteStoreLifecycleQueryOperations queryOperations;

    private FinancingReadOperations(SqliteStoreLifecycleQueryOperations queryOperations) {
      this.queryOperations = queryOperations;
    }

    Optional<FinancingArrangementRecord> findFinancingArrangement(
        FinancingArrangementId financingArrangementId) {
      return queryOperations.findFinancingArrangement(financingArrangementId);
    }

    boolean hasFinancingArrangement(FinancingArrangementId financingArrangementId) {
      return queryOperations.hasFinancingArrangement(financingArrangementId);
    }

    List<FinancingArrangementRecord> financingArrangements() {
      return queryOperations.financingArrangements();
    }
  }

  /** Reads durable Realized Foreign Exchange aggregates. */
  static final class RealizedForeignExchangeReadOperations {
    private final SqliteStoreLifecycleQueryOperations queryOperations;

    private RealizedForeignExchangeReadOperations(
        SqliteStoreLifecycleQueryOperations queryOperations) {
      this.queryOperations = queryOperations;
    }

    Optional<ForeignCurrencyObligationRecord> findForeignCurrencyObligation(
        ForeignCurrencyObligationId foreignCurrencyObligationId) {
      return queryOperations.findForeignCurrencyObligation(foreignCurrencyObligationId);
    }

    boolean hasForeignCurrencyObligation(ForeignCurrencyObligationId foreignCurrencyObligationId) {
      return queryOperations.hasForeignCurrencyObligation(foreignCurrencyObligationId);
    }

    List<ForeignCurrencyObligationRecord> foreignCurrencyObligations() {
      return queryOperations.foreignCurrencyObligations();
    }
  }

  /** Reads immutable payroll-run facts that the Latvian Monthly Payroll context owns. */
  static final class LatvianPayrollReadOperations {
    private final SqliteStoreLatvianPayrollQueryOperations payrollQueryOperations;

    private LatvianPayrollReadOperations(
        SqliteStoreLatvianPayrollQueryOperations payrollQueryOperations) {
      this.payrollQueryOperations = payrollQueryOperations;
    }

    Optional<LatvianPayrollRunRecord> findRun(LatvianPayrollRunId payrollRunId) {
      return payrollQueryOperations.findRun(payrollRunId);
    }

    Optional<LatvianPayrollRunRecord> findRunByOriginPosting(PostingId originPostingId) {
      return payrollQueryOperations.findRunByOriginPosting(originPostingId);
    }

    Optional<LatvianPayrollRunRecord> findActiveRun(
        LatvianPayrollEmployeeReference employeeReference, LatvianPayrollMonth payrollMonth) {
      return payrollQueryOperations.findActiveRun(employeeReference, payrollMonth);
    }

    List<LatvianPayrollRunRecord> runs() {
      return payrollQueryOperations.runs();
    }

    Optional<LatvianPayrollSettlementRecord> findActiveSettlement(
        LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
      return payrollQueryOperations.findActiveSettlement(payrollRunId, settlementKind);
    }

    Optional<LatvianPayrollSettlementRecord> findSettlementByPosting(PostingId originPostingId) {
      return payrollQueryOperations.findSettlementByPosting(originPostingId);
    }

    List<LatvianPayrollSettlementRecord> settlements() {
      return payrollQueryOperations.settlements();
    }
  }
}
