package dev.erst.fingrind.contract.reportmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollSettlementStatus;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.payroll.LatvianPayrollEmployeeReference;
import dev.erst.fingrind.contract.payroll.LatvianPayrollMonth;
import dev.erst.fingrind.contract.payroll.LatvianPayrollRunId;
import dev.erst.fingrind.contract.payroll.LatvianPayrollSettlementKind;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Contract coverage for complete immutable payroll-run and settlement-lineage reports. */
class LatvianPayrollRegisterReportContractTest {
  @Test
  void builderKeepsCalculationAndEverySettlementLifecycleFactInOneReportModel() {
    ReportModel model = LatvianPayrollRegisterReportModelBuilder.buildModel(populatedReport());
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());

    assertEquals("latvian-payroll-register", model.family());
    assertEquals(4, model.sections().size());
    assertEquals(
        List.of(
            "payroll-run-2026-07-employee-001", "employee-001", "2026-07", "2026-07-31", "Active"),
        model.sections().getFirst().rows().getFirst().cells());
    assertEquals("EUR 210.00", model.sections().get(1).rows().getFirst().cells().get(2));
    assertEquals("EUR 998.00", model.sections().get(2).rows().getFirst().cells().get(4));
    assertEquals(2, model.sections().get(3).rows().size());
    assertEquals("Reversed", model.sections().get(3).rows().get(1).cells().get(4));
    assertFalseHeaderContains(csv, "rowId");
    assertEquals("NET_WAGES", csvValue(csv, 0, "settlementKind"));
    assertEquals("active", csvValue(csv, 0, "settlementStatus"));
    assertEquals("STATE_REMITTANCE", csvValue(csv, 1, "settlementKind"));
    assertEquals("reversed", csvValue(csv, 1, "settlementStatus"));
    assertEquals("99800", csvValue(csv, 1, "stateRemittanceMinorUnits"));
  }

  @Test
  void builderRetainsTheExplicitEmptyScopeRecordInsteadOfPublishingAHeaderOnlyCsv() {
    ReportModel model =
        LatvianPayrollRegisterReportModelBuilder.buildModel(
            new LatvianPayrollRegisterReport(ReportModelTestSupport.bookIdentity(), List.of()));
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());

    assertTrue(model.sections().getFirst().rows().isEmpty());
    assertTrue(
        model.sections().getFirst().verdicts().stream()
            .anyMatch(verdict -> verdict.value().contains("No payroll runs matched")));
    assertEquals("latvian-payroll-register", csvValue(csv, 0, "exportFamily"));
    assertTrue(csvValue(csv, 0, "message").contains("No payroll runs matched"));
  }

  @Test
  void builderPublishesInactiveAndUnsettledLifecycleStatesWithoutLosingTabularLineage() {
    LatvianPayrollRegisterReport report =
        new LatvianPayrollRegisterReport(
            ReportModelTestSupport.bookIdentity(), List.of(inactiveUnsettledRow()));
    ReportModel model = LatvianPayrollRegisterReportModelBuilder.INSTANCE.build(report);
    ReportCsvProjection csv = Objects.requireNonNull(model.tabularCsvProjection());

    assertEquals("Reversed", model.sections().getFirst().rows().getFirst().cells().get(4));
    assertEquals("Unsettled", model.sections().get(3).rows().getFirst().cells().get(4));
    assertEquals("reversed", csvValue(csv, 0, "runStatus"));
    assertEquals("posting-payroll-run-reversal-2026-07", csvValue(csv, 0, "runReversalPostingId"));
    assertEquals("unsettled", csvValue(csv, 0, "settlementStatus"));
  }

  @Test
  void queryResultExposesOnlyItsSelectedOutcome() {
    LatvianPayrollRegisterReport report = populatedReport();
    LatvianPayrollRegisterResult.Reported reported =
        new LatvianPayrollRegisterResult.Reported(report);
    BookQueryRejection rejection = new BookQueryRejection.BookNotInitialized();
    LatvianPayrollRegisterResult.Rejected rejected =
        new LatvianPayrollRegisterResult.Rejected(rejection);

    assertSame(report, reported.reported());
    assertNull(reported.rejection());
    assertNull(rejected.reported());
    assertSame(rejection, rejected.rejection());
    assertEquals("reported", reported.fold(value -> "reported", value -> "rejected"));
    assertEquals("rejected", rejected.fold(value -> "reported", value -> "rejected"));
  }

  @Test
  void registerValuesRequireOneCurrencyAndExposeAConcreteQueryValue() {
    LatvianPayrollRegisterRow row = inactiveUnsettledRow();

    assertEquals(new LatvianPayrollRegisterQuery(), new LatvianPayrollRegisterQuery());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new LatvianPayrollRegisterRow(
                row.payrollRunId(),
                row.employeeReference(),
                row.payrollMonth(),
                row.originPostingId(),
                row.effectiveDate(),
                row.reversalPostingId(),
                row.grossWages(),
                new MonetaryAmount("USD", row.employeeSocialContribution().minorUnits()),
                row.employerSocialContribution(),
                row.nonTaxableMinimum(),
                row.personalIncomeTax(),
                row.netWages(),
                row.totalEmployerCost(),
                row.stateRemittance(),
                row.settlements()));
  }

  private static LatvianPayrollRegisterReport populatedReport() {
    List<LatvianPayrollSettlementStatus> settlements =
        List.of(
            new LatvianPayrollSettlementStatus(
                LatvianPayrollSettlementKind.NET_WAGES,
                new PostingId("posting-net-wages-2026-07"),
                LocalDate.parse("2026-07-31"),
                Optional.empty()),
            new LatvianPayrollSettlementStatus(
                LatvianPayrollSettlementKind.STATE_REMITTANCE,
                new PostingId("posting-state-remittance-2026-07"),
                LocalDate.parse("2026-08-05"),
                Optional.of(new PostingId("posting-state-remittance-reversal-2026-07"))));
    return new LatvianPayrollRegisterReport(
        ReportModelTestSupport.bookIdentity(),
        List.of(
            new LatvianPayrollRegisterRow(
                new LatvianPayrollRunId("payroll-run-2026-07-employee-001"),
                new LatvianPayrollEmployeeReference("employee-001"),
                LatvianPayrollMonth.parse("2026-07"),
                new PostingId("posting-payroll-run-2026-07"),
                LocalDate.parse("2026-07-31"),
                Optional.empty(),
                money("200000"),
                money("21000"),
                money("47180"),
                money("55000"),
                money("31620"),
                money("147380"),
                money("247180"),
                money("99800"),
                settlements)));
  }

  private static LatvianPayrollRegisterRow inactiveUnsettledRow() {
    return new LatvianPayrollRegisterRow(
        new LatvianPayrollRunId("payroll-run-2026-07-employee-002"),
        new LatvianPayrollEmployeeReference("employee-002"),
        LatvianPayrollMonth.parse("2026-07"),
        new PostingId("posting-payroll-run-2026-07-employee-002"),
        LocalDate.parse("2026-07-31"),
        Optional.of(new PostingId("posting-payroll-run-reversal-2026-07")),
        money("10000"),
        money("1050"),
        money("2359"),
        money("8950"),
        money("0"),
        money("8950"),
        money("12359"),
        money("3409"),
        List.of());
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static String csvValue(ReportCsvProjection csv, int rowIndex, String header) {
    return csv.rows().get(rowIndex).get(csv.headers().indexOf(header));
  }

  private static void assertFalseHeaderContains(ReportCsvProjection csv, String header) {
    assertFalse(csv.headers().contains(header), csv.headers().toString());
  }
}
