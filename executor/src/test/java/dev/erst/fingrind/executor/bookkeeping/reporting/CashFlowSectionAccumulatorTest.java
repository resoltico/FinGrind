package dev.erst.fingrind.executor.bookkeeping.reporting;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.executor.bookkeeping.CashFlowRowView;
import dev.erst.fingrind.executor.bookkeeping.CashFlowSectionView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for cash-flow row accumulation and ordering. */
class CashFlowSectionAccumulatorTest {
  private static final Instant DECLARED_AT = Instant.parse("2026-05-13T11:00:00Z");

  @Test
  void sections_orderRowsByAccountCodeThenCurrencyCode_and_mergeMatchingBuckets() {
    RegisteredAccount sales =
        account(
            "inventory-a",
            "Sales",
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE));
    RegisteredAccount fees =
        account(
            "inventory-z",
            "Fees",
            AccountType.EXPENSE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    CashFlowSectionAccumulator accumulator = new CashFlowSectionAccumulator();

    accumulator.add(movement(CashFlowSectionKind.OPERATING, sales, "USD", "1.00", "0.00"));
    accumulator.add(movement(CashFlowSectionKind.OPERATING, sales, "USD", "0.50", "0.00"));
    accumulator.add(movement(CashFlowSectionKind.OPERATING, fees, "EUR", "0.00", "2.00"));
    accumulator.add(movement(CashFlowSectionKind.FINANCING, sales, "EUR", "3.00", "0.00"));

    List<CashFlowSectionView> sections = accumulator.sections();
    CashFlowSectionView operatingSection = sections.getFirst();

    assertEquals(CashFlowSectionKind.OPERATING, operatingSection.sectionKind());
    assertEquals(List.of("inventory-a", "inventory-z"), lineCodes(operatingSection));
    assertEquals(
        "USD", operatingSection.rows().getFirst().movement().netAmount().currencyUnit().code());
    assertEquals(
        "EUR", operatingSection.rows().get(1).movement().netAmount().currencyUnit().code());
    assertEquals(
        BalanceMath.currencyBalance(CurrencyUnit.of("USD"), 150L, 0L),
        operatingSection.rows().getFirst().movement());
    assertEquals(CashFlowSectionKind.INVESTING, sections.get(1).sectionKind());
    assertEquals(List.of(), sections.get(1).rows());
    assertEquals(CashFlowSectionKind.FINANCING, sections.get(2).sectionKind());
    assertEquals(List.of("inventory-a"), lineCodes(sections.get(2)));
  }

  @Test
  void sections_orderRowsWithOneAccountByCurrencyCode() {
    RegisteredAccount sales =
        account(
            "inventory-a",
            "Sales",
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE));
    CashFlowSectionAccumulator accumulator = new CashFlowSectionAccumulator();

    accumulator.add(movement(CashFlowSectionKind.OPERATING, sales, "USD", "1.00", "0.00"));
    accumulator.add(movement(CashFlowSectionKind.OPERATING, sales, "EUR", "1.00", "0.00"));

    List<CashFlowRowView> rows = accumulator.sections().getFirst().rows();

    assertEquals(
        List.of("EUR", "USD"),
        rows.stream().map(row -> row.movement().netAmount().currencyUnit().code()).toList());
  }

  @Test
  void rowOrdering_comparesAccountBeforeCurrencyThenCurrencyWithinOneAccount() {
    CashFlowSectionAccumulator.RowKey earlierAccountUsd =
        new CashFlowSectionAccumulator.RowKey(
            CashFlowSectionKind.OPERATING, new AccountCode("inventory-a"), CurrencyUnit.of("USD"));
    CashFlowSectionAccumulator.RowKey laterAccountEur =
        new CashFlowSectionAccumulator.RowKey(
            CashFlowSectionKind.OPERATING, new AccountCode("inventory-z"), CurrencyUnit.of("EUR"));
    CashFlowSectionAccumulator.RowKey earlierCurrency =
        new CashFlowSectionAccumulator.RowKey(
            CashFlowSectionKind.OPERATING, new AccountCode("inventory-a"), CurrencyUnit.of("EUR"));

    assertTrue(CashFlowSectionAccumulator.compareRowKeys(earlierAccountUsd, laterAccountEur) < 0);
    assertTrue(CashFlowSectionAccumulator.compareRowKeys(earlierCurrency, earlierAccountUsd) < 0);
  }

  private static List<String> lineCodes(CashFlowSectionView section) {
    return section.rows().stream().map(row -> row.lineCode()).toList();
  }

  private static CashFlowPostingMovementClassifier.CashFlowRowMovement movement(
      CashFlowSectionKind sectionKind,
      RegisteredAccount account,
      String currencyCode,
      String debitAmount,
      String creditAmount) {
    return new CashFlowPostingMovementClassifier.CashFlowRowMovement(
        sectionKind,
        account,
        BalanceMath.currencyBalance(
            CurrencyUnit.of(currencyCode),
            Money.parse(currencyCode, debitAmount).minorUnits(),
            Money.parse(currencyCode, creditAmount).minorUnits()));
  }

  private static RegisteredAccount account(
      String accountCode,
      String accountName,
      AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy accountTaxonomy) {
    return registeredAccount(
        new AccountCode(accountCode),
        new AccountName(accountName),
        accountType,
        accountTaxonomy,
        true,
        DECLARED_AT);
  }
}
