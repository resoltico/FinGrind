package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayroll2026;
import dev.erst.fingrind.contract.payroll.LatvianMonthlyPayrollCalculation;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Verifies first-defense settlement admission for each durable Latvian payroll obligation. */
class LatvianPayrollSettlementAdmissionPolicyTest {
  private static final LatvianPayrollMonth PAYROLL_MONTH =
      new LatvianPayrollMonth(YearMonth.of(2026, 7));
  private static final LocalDate RUN_DATE = PAYROLL_MONTH.value().atEndOfMonth();
  private static final LatvianPayrollRunId PAYROLL_RUN_ID =
      new LatvianPayrollRunId("payroll-2026-07-employee-1");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final LatvianMonthlyPayrollCalculation CALCULATION =
      LatvianMonthlyPayroll2026.calculate(PAYROLL_MONTH, Money.parse("EUR", "2000.00"));

  private final LatvianPayrollSettlementAdmissionPolicy policy =
      new LatvianPayrollSettlementAdmissionPolicy();

  @Test
  void resolve_leavesNonSettlementEntriesUntouchedAndRejectsImpossibleSettlementStates() {
    BookkeepingEntry.ExpenseSettled ordinaryEntry =
        new BookkeepingEntry.ExpenseSettled(
            RUN_DATE,
            new AccountCode("5000"),
            CASH,
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null);

    LatvianPayrollSettlementAdmissionPolicy.Resolution ordinaryResolution =
        policy.resolve(
            ordinaryEntry, new SettlementBook(Optional.empty(), Optional.empty()), "entry");
    assertSame(ordinaryEntry, ordinaryResolution.entry());
    assertTrue(ordinaryResolution.rejection().isEmpty());

    assertRejection(
        policy.resolve(
            netWages(RUN_DATE), new SettlementBook(Optional.empty(), Optional.empty()), "entry"),
        "latvian-payroll-run-not-found");
    assertRejection(
        policy.resolve(
            netWages(RUN_DATE),
            new SettlementBook(
                Optional.of(run(Optional.of(new PostingId("run-reversal")))), Optional.empty()),
            "entry"),
        "latvian-payroll-run-reversed");
    assertRejection(
        policy.resolve(
            netWages(RUN_DATE.minusDays(1)),
            new SettlementBook(Optional.of(run(Optional.empty())), Optional.empty()),
            "entry"),
        "latvian-payroll-settlement-precedes-run");
    assertRejection(
        policy.resolve(
            netWages(RUN_DATE),
            new SettlementBook(
                Optional.of(run(Optional.empty())),
                Optional.of(
                    new LatvianPayrollSettlementRecord(
                        LatvianPayrollSettlementKind.NET_WAGES,
                        PAYROLL_RUN_ID,
                        new PostingId("existing-net-wages"),
                        RUN_DATE,
                        CASH,
                        Optional.empty()))),
            "entry"),
        "latvian-payroll-settlement-already-exists");
  }

  @Test
  void resolve_bindsEachSettlementKindToTheRunExactObligation() {
    SettlementBook book = new SettlementBook(Optional.of(run(Optional.empty())), Optional.empty());

    LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWages =
        assertInstanceOf(
            LatvianPayrollBookkeepingEntryVariants.NetWageSettlement.class,
            policy.resolve(netWages(RUN_DATE), book, "entry").entry());
    LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance =
        assertInstanceOf(
            LatvianPayrollBookkeepingEntryVariants.StateRemittance.class,
            policy.resolve(stateRemittance(RUN_DATE), book, "entry").entry());

    assertEquals(
        LatvianPayrollSettlementKind.NET_WAGES,
        Objects.requireNonNull(netWages.resolvedSettlement(), "resolvedSettlement")
            .settlementKind());
    assertEquals(
        CALCULATION.netWages(),
        Objects.requireNonNull(netWages.resolvedSettlement(), "resolvedSettlement").netWages());
    assertEquals(
        LatvianPayrollSettlementKind.STATE_REMITTANCE,
        Objects.requireNonNull(stateRemittance.resolvedSettlement(), "resolvedSettlement")
            .settlementKind());
    assertEquals(
        CALCULATION.stateRemittance(),
        Objects.requireNonNull(stateRemittance.resolvedSettlement(), "resolvedSettlement")
            .stateRemittance());
  }

  @Test
  void resolution_requiresAnEntryWheneverThereIsNoRejection() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new LatvianPayrollSettlementAdmissionPolicy.Resolution(null, Optional.empty()));
  }

  @Test
  void payrollRunRecord_distinguishesActiveAndReversedRuns() {
    assertTrue(run(Optional.empty()).active());
    assertFalse(run(Optional.of(new PostingId("run-reversal"))).active());
  }

  @Test
  void payrollRunRecord_requiresTheLastDayOfItsPayrollMonth() {
    LatvianPayrollRunRecord validRun = run(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianPayrollRunRecord(
                validRun.payrollRunId(),
                validRun.employeeReference(),
                validRun.payrollMonth(),
                validRun.effectiveDate().minusDays(1),
                validRun.wageExpenseAccountCode(),
                validRun.employerSocialContributionExpenseAccountCode(),
                validRun.netWagesPayableAccountCode(),
                validRun.employeeSocialContributionPayableAccountCode(),
                validRun.employerSocialContributionPayableAccountCode(),
                validRun.personalIncomeTaxPayableAccountCode(),
                validRun.calculation(),
                validRun.originPostingId(),
                validRun.reversalPostingId()));
  }

  private static void assertRejection(
      LatvianPayrollSettlementAdmissionPolicy.Resolution resolution, String expectedCode) {
    BookkeepingPostingRejection.EntrySemanticsViolations rejection =
        assertInstanceOf(
            BookkeepingPostingRejection.EntrySemanticsViolations.class,
            resolution.rejection().orElseThrow());
    assertEquals(expectedCode, rejection.violations().getFirst().code());
  }

  private static LatvianPayrollBookkeepingEntryVariants.NetWageSettlement netWages(
      LocalDate effectiveDate) {
    return new LatvianPayrollBookkeepingEntryVariants.NetWageSettlement(
        effectiveDate, PAYROLL_RUN_ID, CASH, null);
  }

  private static LatvianPayrollBookkeepingEntryVariants.StateRemittance stateRemittance(
      LocalDate effectiveDate) {
    return new LatvianPayrollBookkeepingEntryVariants.StateRemittance(
        effectiveDate, PAYROLL_RUN_ID, CASH, null);
  }

  private static LatvianPayrollRunRecord run(Optional<PostingId> reversalPostingId) {
    return new LatvianPayrollRunRecord(
        PAYROLL_RUN_ID,
        new LatvianPayrollEmployeeReference("employee-1"),
        PAYROLL_MONTH,
        RUN_DATE,
        new AccountCode("5000"),
        new AccountCode("5010"),
        new AccountCode("2200"),
        new AccountCode("2210"),
        new AccountCode("2220"),
        new AccountCode("2230"),
        CALCULATION,
        new PostingId("payroll-run"),
        reversalPostingId);
  }

  /** Supplies the run and settlement state consulted by the settlement admission policy. */
  private static final class SettlementBook extends EmptyValidationStore {
    private final Optional<LatvianPayrollRunRecord> run;
    private final Optional<LatvianPayrollSettlementRecord> settlement;

    private SettlementBook(
        Optional<LatvianPayrollRunRecord> run,
        Optional<LatvianPayrollSettlementRecord> settlement) {
      this.run = run;
      this.settlement = settlement;
    }

    @Override
    public Optional<LatvianPayrollRunRecord> findLatvianPayrollRun(
        LatvianPayrollRunId payrollRunId) {
      return run;
    }

    @Override
    public Optional<LatvianPayrollSettlementRecord> findActiveLatvianPayrollSettlement(
        LatvianPayrollRunId payrollRunId, LatvianPayrollSettlementKind settlementKind) {
      return settlement.filter(candidate -> candidate.settlementKind() == settlementKind);
    }
  }
}
