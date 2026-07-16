package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.ResolvedLatvianPayrollSettlement;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.LatvianPayrollRunRecord;
import java.time.LocalDate;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Rebuilds a retained executor-resolved Latvian payroll entry from its durable run facts. */
final class SqliteLatvianPayrollOriginatingEntryMapper {
  private SqliteLatvianPayrollOriginatingEntryMapper() {}

  static @Nullable BookkeepingEntry originatingEntry(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      PostingOriginKind postingOriginKind) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    return switch (postingOriginKind) {
      case LATVIAN_MONTHLY_PAYROLL -> monthlyPayrollEntry(activeDatabase, postingId);
      case LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT ->
          settlementEntry(
              activeDatabase,
              postingId,
              LatvianPayrollBookkeepingEntryVariants.NetWageSettlement::new);
      case LATVIAN_PAYROLL_STATE_REMITTANCE ->
          settlementEntry(
              activeDatabase,
              postingId,
              LatvianPayrollBookkeepingEntryVariants.StateRemittance::new);
      default -> null;
    };
  }

  private static BookkeepingEntry monthlyPayrollEntry(
      SqliteNativeDatabase activeDatabase, PostingId postingId) {
    LatvianPayrollRunRecord run =
        SqliteLatvianPayrollStatementQueries.findRunByOriginPosting(activeDatabase, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Latvian payroll posting "
                            + postingId.value()
                            + " has no durable payroll-run facts."));
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
        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(run.calculation().grossWages()),
        run.calculation());
  }

  private static BookkeepingEntry settlementEntry(
      SqliteNativeDatabase activeDatabase,
      PostingId postingId,
      SettlementEntryFactory settlementEntryFactory) {
    var settlement =
        SqliteLatvianPayrollStatementQueries.findSettlementByOriginPosting(
                activeDatabase, postingId)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Latvian payroll settlement posting "
                            + postingId.value()
                            + " has no durable settlement facts."));
    LatvianPayrollRunRecord run =
        SqliteLatvianPayrollStatementQueries.findRun(activeDatabase, settlement.payrollRunId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Latvian payroll settlement posting "
                            + postingId.value()
                            + " has no durable payroll-run facts."));
    ResolvedLatvianPayrollSettlement resolvedSettlement =
        new ResolvedLatvianPayrollSettlement(
            settlement.settlementKind(),
            run.payrollRunId(),
            settlement.cashAccountCode(),
            run.netWagesPayableAccountCode(),
            run.employeeSocialContributionPayableAccountCode(),
            run.employerSocialContributionPayableAccountCode(),
            run.personalIncomeTaxPayableAccountCode(),
            run.calculation().netWages(),
            run.calculation().employeeSocialContribution(),
            run.calculation().employerSocialContribution(),
            run.calculation().personalIncomeTax());
    return settlementEntryFactory.create(
        settlement.effectiveDate(),
        settlement.payrollRunId(),
        settlement.cashAccountCode(),
        resolvedSettlement);
  }

  /** Constructs the one settlement entry type selected by the exhaustive origin-kind switch. */
  @FunctionalInterface
  private interface SettlementEntryFactory {
    /**
     * Creates the retained typed settlement entry from durable settlement and calculation facts.
     */
    BookkeepingEntry create(
        LocalDate effectiveDate,
        LatvianPayrollRunId payrollRunId,
        AccountCode cashAccountCode,
        ResolvedLatvianPayrollSettlement resolvedSettlement);
  }
}
