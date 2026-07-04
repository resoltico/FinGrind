package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Derives truthful display sections for income-statement projections. */
public final class IncomeStatementPresentationSupport {
  private IncomeStatementPresentationSupport() {}

  /** Stable display section codes shared by text, CSV, and PDF projectors. */
  public enum SectionCode {
    REVENUE("REVENUE", "Revenue"),
    COST_OF_SALES("COST_OF_SALES", "Cost of Sales"),
    OTHER_REVENUE_AND_INCOME("OTHER_REVENUE_AND_INCOME", "Other Revenue and Income"),
    EXPENSE("EXPENSE", "Expenses");

    private final String wireValue;
    private final String title;

    SectionCode(String wireValue, String title) {
      this.wireValue = wireValue;
      this.title = title;
    }

    /** Machine-readable code for this display section. */
    public String wireValue() {
      return wireValue;
    }

    /** Human-readable title for this display section. */
    public String title() {
      return title;
    }
  }

  /** One projected display section with stable code and rendered content. */
  public record PresentationSection(
      SectionCode sectionCode, List<IncomeStatementRow> rows, List<CurrencyBalance> totals) {
    /** Validates one presentation section. */
    public PresentationSection {
      Objects.requireNonNull(sectionCode, "sectionCode");
      rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
      totals = List.copyOf(Objects.requireNonNull(totals, "totals"));
    }

    /** Human-readable title for this projected section. */
    public String title() {
      return sectionCode.title();
    }

    /** Returns whether this projected section has rows or totals to render. */
    public boolean hasRenderableContent() {
      return !rows.isEmpty() || !totals.isEmpty();
    }
  }

  /** Returns projected display sections for the current reporting window. */
  public static List<PresentationSection> currentSections(IncomeStatementReport report) {
    Objects.requireNonNull(report, "report");
    return sections(report.bookIdentity().bookDoctrine().bookTemplateId(), report.sections());
  }

  /** Returns projected display sections for the comparative reporting window. */
  public static List<PresentationSection> comparativeSections(IncomeStatementReport report) {
    Objects.requireNonNull(report, "report");
    return sections(
        report.bookIdentity().bookDoctrine().bookTemplateId(), report.comparativeSections());
  }

  static List<CurrencyBalance> aggregateRows(List<IncomeStatementRow> rows) {
    return Objects.requireNonNull(rows, "rows").stream()
        .map(IncomeStatementRow::movement)
        .collect(
            Collectors.toMap(
                balance -> balance.debitTotal().currencyUnit().code(),
                Totals::from,
                Totals::merge,
                java.util.LinkedHashMap::new))
        .values()
        .stream()
        .sorted(Comparator.comparing(totals -> totals.currencyUnit().code()))
        .map(Totals::toCurrencyBalance)
        .toList();
  }

  static boolean contributesToGrossProfit(IncomeStatementRow row) {
    ProfitAndLossLineClassification classification =
        Objects.requireNonNull(row, "row").lineClassification();
    return classification == ProfitAndLossLineClassification.OPERATING_REVENUE
        || classification == ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE
        || classification == ProfitAndLossLineClassification.COST_OF_SALES;
  }

  private static List<PresentationSection> sections(
      BookTemplateId bookTemplateId, List<IncomeStatementSection> sections) {
    Objects.requireNonNull(bookTemplateId, "bookTemplateId");
    Objects.requireNonNull(sections, "sections");
    return bookTemplateId == BookTemplateId.OWNER_MANAGED_TRADING
        ? tradingSections(sections)
        : nominalSections(sections);
  }

  private static List<PresentationSection> nominalSections(List<IncomeStatementSection> sections) {
    return sections.stream()
        .map(
            section ->
                new PresentationSection(
                    switch (section.accountType()) {
                      case REVENUE -> SectionCode.REVENUE;
                      case EXPENSE -> SectionCode.EXPENSE;
                      default ->
                          throw new IllegalArgumentException(
                              "Income statement sections admit only revenue or expense account types.");
                    },
                    section.rows(),
                    section.totals()))
        .toList();
  }

  private static List<PresentationSection> tradingSections(List<IncomeStatementSection> sections) {
    return List.of(
        section(
            SectionCode.REVENUE,
            filterRows(sections, IncomeStatementPresentationSupport::isGrossRevenue)),
        section(
            SectionCode.COST_OF_SALES,
            filterRows(sections, IncomeStatementPresentationSupport::isCostOfSales)),
        section(
            SectionCode.OTHER_REVENUE_AND_INCOME,
            filterRows(sections, IncomeStatementPresentationSupport::isOtherRevenueOrIncome)),
        section(
            SectionCode.EXPENSE,
            filterRows(sections, IncomeStatementPresentationSupport::isExpense)));
  }

  private static PresentationSection section(
      SectionCode sectionCode, List<IncomeStatementRow> rows) {
    return new PresentationSection(sectionCode, rows, aggregateRows(rows));
  }

  private static List<IncomeStatementRow> filterRows(
      List<IncomeStatementSection> sections, Predicate<IncomeStatementRow> predicate) {
    return sections.stream().flatMap(section -> section.rows().stream()).filter(predicate).toList();
  }

  private static boolean isGrossRevenue(IncomeStatementRow row) {
    ProfitAndLossLineClassification classification =
        Objects.requireNonNull(row, "row").lineClassification();
    return classification == ProfitAndLossLineClassification.OPERATING_REVENUE
        || classification == ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE;
  }

  private static boolean isCostOfSales(IncomeStatementRow row) {
    return Objects.requireNonNull(row, "row").lineClassification()
        == ProfitAndLossLineClassification.COST_OF_SALES;
  }

  private static boolean isOtherRevenueOrIncome(IncomeStatementRow row) {
    return Objects.requireNonNull(row, "row").lineType() == AccountType.REVENUE
        && !isGrossRevenue(row);
  }

  private static boolean isExpense(IncomeStatementRow row) {
    return Objects.requireNonNull(row, "row").lineType() == AccountType.EXPENSE
        && !isCostOfSales(row);
  }

  /** Immutable running totals for one currency bucket. */
  private record Totals(CurrencyUnit currencyUnit, long debitMinorUnits, long creditMinorUnits) {
    private Totals {
      Objects.requireNonNull(currencyUnit, "currencyUnit");
    }

    private static Totals from(CurrencyBalance balance) {
      return new Totals(
          balance.debitTotal().currencyUnit(),
          balance.debitTotal().minorUnits(),
          balance.creditTotal().minorUnits());
    }

    private Totals merge(Totals other) {
      return new Totals(
          currencyUnit,
          Math.addExact(debitMinorUnits, other.debitMinorUnits),
          Math.addExact(creditMinorUnits, other.creditMinorUnits));
    }

    private CurrencyBalance toCurrencyBalance() {
      return CurrencyBalance.ofTotals(
          Money.ofMinorUnits(currencyUnit, debitMinorUnits),
          Money.ofMinorUnits(currencyUnit, creditMinorUnits));
    }
  }
}
