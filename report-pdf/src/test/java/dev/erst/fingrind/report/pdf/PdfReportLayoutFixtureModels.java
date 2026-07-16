package dev.erst.fingrind.report.pdf;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerPagination;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowRow;
import dev.erst.fingrind.contract.bookkeeping.CashFlowSection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionSection;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementSection;
import dev.erst.fingrind.contract.bookkeeping.PeriodCurrencySummary;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceRow;
import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ReportModel;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Dedicated sample report models for PDF layout and raster snapshot coverage. */
final class PdfReportLayoutFixtureModels {
  private PdfReportLayoutFixtureModels() {}

  static ReportModel sampleAccountBalanceModel() {
    return AccountBalanceReportModelBuilder.buildModel(
        new AccountBalanceSnapshot(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            PdfReportFixtureSupport.CASH_ACCOUNT,
            Optional.of(LocalDate.parse("2026-04-01")),
            Optional.of(LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))));
  }

  static ReportModel sampleTrialBalanceModel() {
    return TrialBalanceReportModelBuilder.buildModel(
        PdfReportFixtureSupport.trialBalanceReport(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                new TrialBalanceRow(
                    PdfReportFixtureSupport.CASH_ACCOUNT,
                    PdfReportFixtureSupport.balance(
                        "EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT)),
                new TrialBalanceRow(
                    PdfReportFixtureSupport.REVENUE_ACCOUNT,
                    PdfReportFixtureSupport.balance(
                        "EUR", "10.00", "1250.00", "1240.00", BalanceSide.CREDIT))),
            List.of(
                new TrialBalanceRow(
                    PdfReportFixtureSupport.CASH_ACCOUNT,
                    PdfReportFixtureSupport.balance(
                        "EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT)))));
  }

  static ReportModel sampleAccountLedgerModel() {
    return AccountLedgerReportModelBuilder.buildModel(
        new AccountLedgerReport(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            PdfReportFixtureSupport.CASH_ACCOUNT,
            new EffectiveDateRange.Bounded(
                LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            AccountLedgerPagination.firstPage(50),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "250.00", "0.00", "250.00", BalanceSide.DEBIT)),
            List.of(
                new AccountLedgerEntry(
                    PdfReportFixtureSupport.postingFact(0, "100.00"),
                    PdfReportFixtureSupport.balance(
                        "EUR", "100.00", "0.00", "100.00", BalanceSide.DEBIT),
                    PdfReportFixtureSupport.money("EUR", "350.00"),
                    BalanceSide.DEBIT),
                new AccountLedgerEntry(
                    PdfReportFixtureSupport.postingFact(1, "50.00"),
                    PdfReportFixtureSupport.balance(
                        "EUR", "150.00", "0.00", "150.00", BalanceSide.DEBIT),
                    PdfReportFixtureSupport.money("EUR", "400.00"),
                    BalanceSide.DEBIT)),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "400.00", "0.00", "400.00", BalanceSide.DEBIT))));
  }

  static ReportModel samplePeriodSummaryModel() {
    return PeriodSummaryReportModelBuilder.buildModel(
        new PeriodSummaryReport(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            PostingCoverage.ALL_POSTING_KINDS,
            2,
            4,
            2,
            List.of(
                new PeriodCurrencySummary(
                    PdfReportFixtureSupport.balance(
                        "EUR", "150.00", "150.00", "0.00", BalanceSide.ZERO))),
            PdfReportFixtureSupport.accountActivityRows(2)));
  }

  static ReportModel sampleFinancialPositionModel() {
    return FinancialPositionReportModelBuilder.buildModel(
        new FinancialPositionReport(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            Optional.of(LocalDate.parse("2026-04-30")),
            Optional.of(LocalDate.parse("2026-04-30")),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            true,
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        PdfReportFixtureSupport.financialPositionRow(
                            "1000",
                            "Cash and Cash Equivalents",
                            AccountType.ASSET,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            PdfReportFixtureSupport.balance(
                                "EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "1250.00", "10.00", "1240.00", BalanceSide.DEBIT))),
                new FinancialPositionSection(
                    AccountType.EQUITY,
                    List.of(
                        PdfReportFixtureSupport.financialPositionRow(
                            "3000",
                            "Contributed Capital",
                            AccountType.EQUITY,
                            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                            PdfReportFixtureSupport.balance(
                                "EUR", "0.00", "1240.00", "1240.00", BalanceSide.CREDIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "0.00", "1240.00", "1240.00", BalanceSide.CREDIT)))),
            List.of(
                new FinancialPositionSection(
                    AccountType.ASSET,
                    List.of(
                        PdfReportFixtureSupport.financialPositionRow(
                            "1000",
                            "Prior Cash and Cash Equivalents",
                            AccountType.ASSET,
                            FinancialPositionLineClassification.CURRENT_ASSET,
                            PdfReportFixtureSupport.balance(
                                "EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "1000.00", "10.00", "990.00", BalanceSide.DEBIT))),
                new FinancialPositionSection(
                    AccountType.EQUITY,
                    List.of(
                        PdfReportFixtureSupport.financialPositionRow(
                            "3000",
                            "Prior Contributed Capital",
                            AccountType.EQUITY,
                            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                            PdfReportFixtureSupport.balance(
                                "EUR", "0.00", "990.00", "990.00", BalanceSide.CREDIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "0.00", "990.00", "990.00", BalanceSide.CREDIT))))));
  }

  static ReportModel sampleIncomeStatementModel() {
    return IncomeStatementReportModelBuilder.buildModel(
        new IncomeStatementReport(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        PdfReportFixtureSupport.incomeStatementRow(
                            "4000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            PdfReportFixtureSupport.balance(
                                "EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)))),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "2500.00", "2500.00", BalanceSide.CREDIT)),
            List.of(
                new IncomeStatementSection(
                    AccountType.REVENUE,
                    List.of(
                        PdfReportFixtureSupport.incomeStatementRow(
                            "4000",
                            "Prior Subscription Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            PdfReportFixtureSupport.balance(
                                "EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT)))),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "1750.00", "1750.00", BalanceSide.CREDIT))));
  }

  static ReportModel sampleCashFlowStatementModel() {
    return CashFlowStatementReportModelBuilder.buildModel(
        new CashFlowStatementReport(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT)),
            List.of(
                new CashFlowSection(
                    dev.erst.fingrind.core.CashFlowSectionKind.OPERATING,
                    List.of(
                        new CashFlowRow(
                            "2000",
                            "Subscription Revenue",
                            AccountType.REVENUE,
                            Optional.empty(),
                            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                            StatementLineKind.DECLARED_ACCOUNT,
                            PdfReportFixtureSupport.balance(
                                "EUR", "25.00", "0.00", "25.00", BalanceSide.DEBIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "25.00", "0.00", "25.00", BalanceSide.DEBIT))),
                new CashFlowSection(
                    dev.erst.fingrind.core.CashFlowSectionKind.FINANCING,
                    List.of(
                        new CashFlowRow(
                            "3000",
                            "Contributed Capital",
                            AccountType.EQUITY,
                            Optional.of(FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
                            Optional.empty(),
                            StatementLineKind.DECLARED_ACCOUNT,
                            PdfReportFixtureSupport.balance(
                                "EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT)))),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "30.00", "0.00", "30.00", BalanceSide.DEBIT)),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "40.00", "0.00", "40.00", BalanceSide.DEBIT)),
            List.of(
                PdfReportFixtureSupport.balance("EUR", "5.00", "0.00", "5.00", BalanceSide.DEBIT)),
            List.of(
                new CashFlowSection(
                    dev.erst.fingrind.core.CashFlowSectionKind.OPERATING,
                    List.of(
                        new CashFlowRow(
                            "2000",
                            "Prior Subscription Revenue",
                            AccountType.REVENUE,
                            Optional.empty(),
                            Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                            StatementLineKind.DECLARED_ACCOUNT,
                            PdfReportFixtureSupport.balance(
                                "EUR", "20.00", "0.00", "20.00", BalanceSide.DEBIT))),
                    List.of(
                        PdfReportFixtureSupport.balance(
                            "EUR", "20.00", "0.00", "20.00", BalanceSide.DEBIT)))),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "20.00", "0.00", "20.00", BalanceSide.DEBIT)),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "25.00", "0.00", "25.00", BalanceSide.DEBIT))));
  }

  static ReportModel sampleChangesInEquityModel() {
    return ChangesInEquityReportModelBuilder.buildModel(
        new ChangesInEquityReport(
            PdfReportFixtureSupport.BOOK_IDENTITY,
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.of(LocalDate.parse("2025-04-01"), LocalDate.parse("2025-04-30")),
            PostingCoverage.ALL_POSTING_KINDS,
            List.of(
                PdfReportFixtureSupport.changesInEquityRow(
                    "3000",
                    "Contributed Capital",
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                    PdfReportFixtureSupport.balance(
                        "EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT),
                    PdfReportFixtureSupport.balance(
                        "EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT),
                    PdfReportFixtureSupport.balance(
                        "EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT))),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT)),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "250.00", "250.00", BalanceSide.CREDIT)),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "1250.00", "1250.00", BalanceSide.CREDIT)),
            List.of(
                PdfReportFixtureSupport.changesInEquityRow(
                    "3000",
                    "Prior Contributed Capital",
                    FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
                    PdfReportFixtureSupport.balance(
                        "EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT),
                    PdfReportFixtureSupport.balance(
                        "EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT),
                    PdfReportFixtureSupport.balance(
                        "EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT))),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "800.00", "800.00", BalanceSide.CREDIT)),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "200.00", "200.00", BalanceSide.CREDIT)),
            List.of(
                PdfReportFixtureSupport.balance(
                    "EUR", "0.00", "1000.00", "1000.00", BalanceSide.CREDIT))));
  }
}
