package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Persists payroll-run facts and compensating reversals inside the posting transaction. */
final class SqliteLatvianPayrollWriter {
  private SqliteLatvianPayrollWriter() {}

  static void persist(SqliteNativeDatabase activeDatabase, CommittedPosting postingFact) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(postingFact, "postingFact");
    BookkeepingEntry resolvedOrCallerEntry =
        postingFact
            .resolvedOriginatingEntry()
            .orElse(postingFact.callerAuthoredEntry().orElse(null));
    switch (resolvedOrCallerEntry) {
      case LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll ->
          insertRun(activeDatabase, postingFact, payroll);
      case LatvianPayrollBookkeepingEntryVariants.NetWageSettlement settlement ->
          insertSettlement(activeDatabase, postingFact, settlement.resolvedSettlement());
      case LatvianPayrollBookkeepingEntryVariants.StateRemittance settlement ->
          insertSettlement(activeDatabase, postingFact, settlement.resolvedSettlement());
      case BookkeepingEntry.Reversal reversal ->
          insertReversalIfPayrollLifecycleFact(
              activeDatabase, postingFact, reversal.reversal().reference().priorPostingId());
      case null, default -> {}
    }
  }

  private static void insertRun(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      LatvianPayrollBookkeepingEntryVariants.MonthlyPayroll payroll) {
    LatvianMonthlyPayrollCalculation calculation =
        Objects.requireNonNull(
            payroll.resolvedCalculation(),
            "Persisted Latvian payroll requires executor-resolved calculation.");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteLatvianPayrollSql.INSERT_RUN)) {
      statement.bindText(1, payroll.payrollRunId().value());
      statement.bindText(2, postingFact.postingId().value());
      statement.bindText(3, payroll.employeeReference().value());
      statement.bindText(4, payroll.payrollMonth().wireValue());
      statement.bindText(5, CanonicalTemporalText.formatLocalDate(payroll.effectiveDate()));
      statement.bindText(6, payroll.wageExpenseAccountCode().value());
      statement.bindText(7, payroll.employerSocialContributionExpenseAccountCode().value());
      statement.bindText(8, payroll.netWagesPayableAccountCode().value());
      statement.bindText(9, payroll.employeeSocialContributionPayableAccountCode().value());
      statement.bindText(10, payroll.employerSocialContributionPayableAccountCode().value());
      statement.bindText(11, payroll.personalIncomeTaxPayableAccountCode().value());
      statement.bindText(12, calculation.grossWages().currencyUnit().code());
      statement.bindLong(13, calculation.grossWages().minorUnits());
      statement.bindLong(14, calculation.employeeSocialContribution().minorUnits());
      statement.bindLong(15, calculation.employerSocialContribution().minorUnits());
      statement.bindLong(16, calculation.monthlyNonTaxableMinimum().minorUnits());
      statement.bindLong(17, calculation.personalIncomeTax().minorUnits());
      statement.bindLong(18, calculation.netWages().minorUnits());
      statement.step();
    }
  }

  private static void insertSettlement(
      SqliteNativeDatabase activeDatabase,
      CommittedPosting postingFact,
      @Nullable ResolvedLatvianPayrollSettlement settlement) {
    ResolvedLatvianPayrollSettlement requiredSettlement =
        Objects.requireNonNull(
            settlement, "Persisted Latvian payroll settlement requires executor-resolved facts.");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteLatvianPayrollSql.INSERT_SETTLEMENT)) {
      statement.bindText(1, postingFact.postingId().value());
      statement.bindText(2, requiredSettlement.payrollRunId().value());
      statement.bindText(3, requiredSettlement.settlementKind().wireValue());
      statement.bindText(
          4, CanonicalTemporalText.formatLocalDate(postingFact.journalEntry().effectiveDate()));
      statement.bindText(5, requiredSettlement.cashAccountCode().value());
      statement.step();
    }
  }

  private static void insertReversalIfPayrollLifecycleFact(
      SqliteNativeDatabase activeDatabase, CommittedPosting postingFact, PostingId priorPostingId) {
    if (SqliteLatvianPayrollStatementQueries.findSettlementByOriginPosting(
            activeDatabase, priorPostingId)
        .isPresent()) {
      try (SqliteNativeStatement statement =
          activeDatabase.prepare(SqliteLatvianPayrollSql.INSERT_SETTLEMENT_REVERSAL)) {
        statement.bindText(1, postingFact.postingId().value());
        statement.bindText(2, priorPostingId.value());
        statement.step();
      }
      return;
    }
    SqliteLatvianPayrollStatementQueries.findRunByOriginPosting(activeDatabase, priorPostingId)
        .ifPresent(
            run -> {
              try (SqliteNativeStatement statement =
                  activeDatabase.prepare(SqliteLatvianPayrollSql.INSERT_REVERSAL)) {
                statement.bindText(1, postingFact.postingId().value());
                statement.bindText(2, run.payrollRunId().value());
                statement.step();
              }
            });
  }
}
