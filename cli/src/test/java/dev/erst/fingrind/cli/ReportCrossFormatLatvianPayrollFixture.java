package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterReport;
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
import java.util.Optional;

/** Sample retained payroll lifecycle facts used by report-projection tests. */
final class ReportCrossFormatLatvianPayrollFixture {
  private ReportCrossFormatLatvianPayrollFixture() {}

  static LatvianPayrollRegisterReport sampleLatvianPayrollRegisterReport() {
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
        CliFixtureSupport.bookIdentity(),
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

  static LatvianPayrollRegisterReport emptyLatvianPayrollRegisterReport() {
    return new LatvianPayrollRegisterReport(CliFixtureSupport.bookIdentity(), List.of());
  }

  static LatvianPayrollRegisterReport lifecycleLatvianPayrollRegisterReport() {
    List<LatvianPayrollSettlementStatus> reversedSettlements =
        List.of(
            new LatvianPayrollSettlementStatus(
                LatvianPayrollSettlementKind.NET_WAGES,
                new PostingId("posting-net-wages-2026-05"),
                LocalDate.parse("2026-06-03"),
                Optional.of(new PostingId("posting-net-wages-reversal-2026-05"))));
    return new LatvianPayrollRegisterReport(
        CliFixtureSupport.bookIdentity(),
        List.of(
            new LatvianPayrollRegisterRow(
                new LatvianPayrollRunId("payroll-run-2026-06-employee-001"),
                new LatvianPayrollEmployeeReference("employee-001"),
                LatvianPayrollMonth.parse("2026-06"),
                new PostingId("posting-payroll-run-2026-06"),
                LocalDate.parse("2026-06-30"),
                Optional.empty(),
                money("180000"),
                money("18900"),
                money("42462"),
                money("50000"),
                money("27810"),
                money("133290"),
                money("222462"),
                money("89572"),
                List.of()),
            new LatvianPayrollRegisterRow(
                new LatvianPayrollRunId("payroll-run-2026-05-employee-002"),
                new LatvianPayrollEmployeeReference("employee-002"),
                LatvianPayrollMonth.parse("2026-05"),
                new PostingId("posting-payroll-run-2026-05"),
                LocalDate.parse("2026-05-31"),
                Optional.of(new PostingId("posting-payroll-run-reversal-2026-05")),
                money("160000"),
                money("16800"),
                money("37744"),
                money("50000"),
                money("23160"),
                money("120040"),
                money("197744"),
                money("77704"),
                reversedSettlements)));
  }

  private static MonetaryAmount money(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }
}
