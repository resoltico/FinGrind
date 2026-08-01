package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookDoctrines;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.StatementLineKind;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Coverage tests for income-statement presentation partitioning and section semantics. */
class IncomeStatementPresentationSupportCoverageTest {
  @Test
  void presentationSection_reportsRenderableContentForRowsAndTotals() {
    IncomeStatementPresentationSupport.PresentationSection empty =
        new IncomeStatementPresentationSupport.PresentationSection(
            IncomeStatementPresentationSupport.SectionCode.REVENUE, List.of(), List.of());
    IncomeStatementPresentationSupport.PresentationSection rowsOnly =
        new IncomeStatementPresentationSupport.PresentationSection(
            IncomeStatementPresentationSupport.SectionCode.REVENUE,
            List.of(
                row(
                    "4100",
                    "Sales Revenue",
                    AccountType.REVENUE,
                    ProfitAndLossLineClassification.OPERATING_REVENUE,
                    "0.00",
                    "100.00")),
            List.of());
    IncomeStatementPresentationSupport.PresentationSection totalsOnly =
        new IncomeStatementPresentationSupport.PresentationSection(
            IncomeStatementPresentationSupport.SectionCode.REVENUE,
            List.of(),
            List.of(balance("EUR", "0.00", "100.00")));

    assertFalse(empty.hasRenderableContent());
    assertTrue(rowsOnly.hasRenderableContent());
    assertTrue(totalsOnly.hasRenderableContent());
  }

  @Test
  void currentSections_projectNominalRevenueAndExpenseAndRejectInvalidNominalTypes() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            serviceBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                section(
                    AccountType.REVENUE,
                    List.of(
                        row(
                            "4100",
                            "Sales Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            "0.00",
                            "100.00"))),
                section(
                    AccountType.EXPENSE,
                    List.of(
                        row(
                            "6100",
                            "Operating Expense",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.OPERATING_EXPENSE,
                            "20.00",
                            "0.00")))),
            List.of(balance("EUR", "20.00", "100.00")),
            List.of(),
            List.of());

    assertIterableEquals(
        List.of(
            IncomeStatementPresentationSupport.SectionCode.REVENUE,
            IncomeStatementPresentationSupport.SectionCode.EXPENSE),
        IncomeStatementPresentationSupport.currentSections(report).stream()
            .map(IncomeStatementPresentationSupport.PresentationSection::sectionCode)
            .toList());

    IncomeStatementReport invalid =
        new IncomeStatementReport(
            serviceBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(section(AccountType.ASSET, List.of())),
            List.of(),
            List.of(),
            List.of());

    IllegalArgumentException violation =
        assertThrows(
            IllegalArgumentException.class,
            () -> IncomeStatementPresentationSupport.currentSections(invalid));
    assertEquals(
        "Income statement sections admit only revenue or expense account types.",
        violation.getMessage());
  }

  @Test
  void currentSections_partitionTradingRowsIntoGrossProfitOtherIncomeAndOperatingExpenses() {
    IncomeStatementReport report =
        new IncomeStatementReport(
            tradingBookIdentity(),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"),
            EffectiveDateRange.unbounded(),
            PostingCoverage.NON_CLOSING_POSTINGS,
            List.of(
                section(
                    AccountType.REVENUE,
                    List.of(
                        row(
                            "4100",
                            "Sales Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            "0.00",
                            "100.00"),
                        row(
                            "4110",
                            "Sales Discount",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE,
                            "5.00",
                            "0.00"),
                        row(
                            "4200",
                            "Grant Income",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OTHER_REVENUE,
                            "0.00",
                            "20.00"),
                        row(
                            "4300",
                            "Interest Income",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.FINANCE_INCOME,
                            "0.00",
                            "3.00"))),
                section(
                    AccountType.EXPENSE,
                    List.of(
                        row(
                            "5100",
                            "Cost of Sales",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.COST_OF_SALES,
                            "40.00",
                            "0.00"),
                        row(
                            "6100",
                            "Operating Expense",
                            AccountType.EXPENSE,
                            ProfitAndLossLineClassification.OPERATING_EXPENSE,
                            "10.00",
                            "0.00")))),
            List.of(balance("EUR", "55.00", "123.00")),
            List.of(
                section(
                    AccountType.REVENUE,
                    List.of(
                        row(
                            "4100",
                            "Comparative Revenue",
                            AccountType.REVENUE,
                            ProfitAndLossLineClassification.OPERATING_REVENUE,
                            "0.00",
                            "90.00")))),
            List.of(balance("EUR", "0.00", "90.00")));

    List<IncomeStatementPresentationSupport.PresentationSection> currentSections =
        IncomeStatementPresentationSupport.currentSections(report);
    List<IncomeStatementPresentationSupport.PresentationSection> comparativeSections =
        IncomeStatementPresentationSupport.comparativeSections(report);

    assertIterableEquals(
        List.of(
            IncomeStatementPresentationSupport.SectionCode.REVENUE,
            IncomeStatementPresentationSupport.SectionCode.COST_OF_SALES,
            IncomeStatementPresentationSupport.SectionCode.OTHER_REVENUE_AND_INCOME,
            IncomeStatementPresentationSupport.SectionCode.EXPENSE),
        currentSections.stream()
            .map(IncomeStatementPresentationSupport.PresentationSection::sectionCode)
            .toList());
    assertEquals(
        List.of("4100", "4110"),
        currentSections.get(0).rows().stream().map(IncomeStatementRow::lineCode).toList());
    assertEquals(
        List.of("5100"),
        currentSections.get(1).rows().stream().map(IncomeStatementRow::lineCode).toList());
    assertEquals(
        List.of("4200", "4300"),
        currentSections.get(2).rows().stream().map(IncomeStatementRow::lineCode).toList());
    assertEquals(
        List.of("6100"),
        currentSections.get(3).rows().stream().map(IncomeStatementRow::lineCode).toList());
    assertEquals(
        List.of(
            IncomeStatementPresentationSupport.SectionCode.REVENUE,
            IncomeStatementPresentationSupport.SectionCode.COST_OF_SALES,
            IncomeStatementPresentationSupport.SectionCode.OTHER_REVENUE_AND_INCOME,
            IncomeStatementPresentationSupport.SectionCode.EXPENSE),
        comparativeSections.stream()
            .map(IncomeStatementPresentationSupport.PresentationSection::sectionCode)
            .toList());
    assertEquals(List.of(balance("EUR", "5.00", "100.00")), currentSections.get(0).totals());
  }

  private static IncomeStatementSection section(
      AccountType accountType, List<IncomeStatementRow> rows) {
    return new IncomeStatementSection(
        accountType, rows, IncomeStatementPresentationSupport.aggregateRows(rows));
  }

  private static IncomeStatementRow row(
      String lineCode,
      String lineName,
      AccountType lineType,
      ProfitAndLossLineClassification classification,
      String debitAmount,
      String creditAmount) {
    return new IncomeStatementRow(
        lineCode,
        lineName,
        lineType,
        classification,
        StatementLineKind.DECLARED_ACCOUNT,
        balance("EUR", debitAmount, creditAmount));
  }

  private static CurrencyBalance balance(
      String currencyCode, String debitAmount, String creditAmount) {
    return CurrencyBalance.ofTotals(
        dev.erst.fingrind.core.Money.parse(currencyCode, debitAmount),
        dev.erst.fingrind.core.Money.parse(currencyCode, creditAmount));
  }

  private static BookIdentity serviceBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_SERVICE,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }

  private static BookIdentity tradingBookIdentity() {
    return new BookIdentity(
        new EntityProfile(new BookEntityName("Acme Studio")),
        BookDoctrines.INTERNAL_MANAGEMENT_OWNER_MANAGED_TRADING,
        CurrencyUnit.of("EUR"),
        FiscalYearStart.parse("01-01"),
        LocalDate.parse("2026-01-01"));
  }
}
