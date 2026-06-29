package dev.erst.fingrind.executor.bookkeeping.reporting;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.profitAndLossTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.registeredAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceMath;
import dev.erst.fingrind.core.CashFlowSectionKind;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
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
            "2000",
            "Sales",
            AccountType.REVENUE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_REVENUE));
    RegisteredAccount fees =
        account(
            "1000",
            "Fees",
            AccountType.EXPENSE,
            profitAndLossTaxonomy(ProfitAndLossLineClassification.OPERATING_EXPENSE));
    CashFlowSectionAccumulator accumulator = new CashFlowSectionAccumulator();

    accumulator.add(movement(CashFlowSectionKind.OPERATING, sales, "USD", "1.00", "0.00"));
    accumulator.add(movement(CashFlowSectionKind.OPERATING, sales, "EUR", "1.00", "0.00"));
    accumulator.add(movement(CashFlowSectionKind.OPERATING, fees, "EUR", "0.00", "2.00"));
    accumulator.add(movement(CashFlowSectionKind.OPERATING, sales, "EUR", "0.50", "0.00"));

    CashFlowSectionView operatingSection = accumulator.sections().getFirst();

    assertEquals(CashFlowSectionKind.OPERATING, operatingSection.sectionKind());
    assertEquals(List.of("1000", "2000", "2000"), lineCodes(operatingSection));
    assertEquals(
        "EUR", operatingSection.rows().get(1).movement().netAmount().currencyUnit().code());
    assertEquals(
        "USD", operatingSection.rows().get(2).movement().netAmount().currencyUnit().code());
    assertEquals(
        BalanceMath.currencyBalance(CurrencyUnit.of("EUR"), 150L, 0L),
        operatingSection.rows().get(1).movement());
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
