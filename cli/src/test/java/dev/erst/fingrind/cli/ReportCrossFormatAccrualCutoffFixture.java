package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleReport;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleRow;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccrualCutoffKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Sample accrual cut-off lifecycle balances used by output-projection tests. */
final class ReportCrossFormatAccrualCutoffFixture {
  private ReportCrossFormatAccrualCutoffFixture() {}

  static AccrualCutoffScheduleReport sampleAccrualCutoffScheduleReport() {
    AccrualCutoffScheduleRow prepayment =
        new AccrualCutoffScheduleRow(
            new AccrualCutoffId("prepayment-2026"),
            AccrualCutoffKind.PREPAYMENT,
            LocalDate.parse("2026-04-01"),
            new AccountCode("prepaid-insurance"),
            new AccountCode("insurance-expense"),
            new MonetaryAmount("EUR", "12000"),
            new MonetaryAmount("EUR", "3000"),
            new MonetaryAmount("EUR", "9000"),
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-12-31")),
            Optional.of(LocalDate.parse("2026-04-30")));
    AccrualCutoffScheduleRow accruedExpense =
        new AccrualCutoffScheduleRow(
            new AccrualCutoffId("accrued-expense-2026"),
            AccrualCutoffKind.ACCRUED_EXPENSE,
            LocalDate.parse("2026-05-01"),
            new AccountCode("accrued-legal-expense"),
            new AccountCode("legal-expense"),
            new MonetaryAmount("EUR", "5000"),
            new MonetaryAmount("EUR", "2000"),
            new MonetaryAmount("EUR", "3000"),
            Optional.empty(),
            Optional.empty(),
            Optional.of(LocalDate.parse("2026-05-15")));
    return new AccrualCutoffScheduleReport(
        CliFixtureSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-04-30")),
        List.of(prepayment, accruedExpense));
  }
}
