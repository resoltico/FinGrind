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
                new PostingId("eb9b38b4-f216-3db2-89aa-b322dadcc5c4"),
                LocalDate.parse("2026-07-31"),
                Optional.empty()),
            new LatvianPayrollSettlementStatus(
                LatvianPayrollSettlementKind.STATE_REMITTANCE,
                new PostingId("210633ad-7df4-3735-a675-6fde1a7f2c55"),
                LocalDate.parse("2026-08-05"),
                Optional.of(new PostingId("d5317ac7-18ec-33f3-be60-a4f9a6b91606"))));
    return new LatvianPayrollRegisterReport(
        CliFixtureSupport.bookIdentity(),
        List.of(
            new LatvianPayrollRegisterRow(
                new LatvianPayrollRunId("payroll-run-2026-07-employee-001"),
                new LatvianPayrollEmployeeReference("employee-001"),
                LatvianPayrollMonth.parse("2026-07"),
                new PostingId("ddd6d3cc-2763-34be-b4c2-f1dd10218cbd"),
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
                new PostingId("c9ac152f-17d9-35c4-8e6a-9a596da21b03"),
                LocalDate.parse("2026-06-03"),
                Optional.of(new PostingId("b80b6a53-1882-3abd-94eb-14dbea809269"))));
    return new LatvianPayrollRegisterReport(
        CliFixtureSupport.bookIdentity(),
        List.of(
            new LatvianPayrollRegisterRow(
                new LatvianPayrollRunId("payroll-run-2026-06-employee-001"),
                new LatvianPayrollEmployeeReference("employee-001"),
                LatvianPayrollMonth.parse("2026-06"),
                new PostingId("d9b54bf0-c0eb-3717-8d6a-661cfdba371e"),
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
                new PostingId("291ba868-563e-325f-befd-e90c52df322f"),
                LocalDate.parse("2026-05-31"),
                Optional.of(new PostingId("2a645a3f-ac06-3a32-88bb-e7697a7af590")),
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
