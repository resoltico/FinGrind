package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterRow;
import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterReport;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterRow;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Sample fixed-asset, financing, and realized-FX register facts for output tests. */
final class ReportCrossFormatLifecycleContextFixture {
  private ReportCrossFormatLifecycleContextFixture() {}

  static FixedAssetRegisterReport fixedAssetRegisterReport() {
    return new FixedAssetRegisterReport(
        CliFixtureSupport.bookIdentity(),
        Optional.of(LocalDate.parse("2026-07-01")),
        List.of(
            new FixedAssetRegisterRow(
                new FixedAssetId("asset-vehicle-001"),
                LocalDate.parse("2026-06-01"),
                new AccountCode("fixed-asset"),
                new AccountCode("accumulated-depreciation"),
                amount("EUR", "12000"),
                amount("EUR", "1000"),
                amount("EUR", "11000"),
                new FixedAssetDepreciationSchedule(
                    LocalDate.parse("2026-06-01"), 12, amount("EUR", "0")),
                1,
                Optional.of(LocalDate.parse("2026-07-01")),
                Optional.of(LocalDate.parse("2026-07-01")))));
  }

  static FinancingRegisterReport financingRegisterReport() {
    return new FinancingRegisterReport(
        CliFixtureSupport.bookIdentity(),
        List.of(
            new FinancingRegisterRow(
                new FinancingArrangementId("loan-working-capital-001"),
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-04"),
                new AccountCode("financing-principal"),
                new AccountCode("financing-interest-payable"),
                amount("EUR", "10000"),
                amount("EUR", "4000"),
                amount("EUR", "6000"),
                amount("EUR", "500"),
                amount("EUR", "500"),
                amount("EUR", "0"))));
  }

  static RealizedForeignExchangeRegisterReport realizedForeignExchangeRegisterReport() {
    return new RealizedForeignExchangeRegisterReport(
        CliFixtureSupport.bookIdentity(),
        List.of(
            new RealizedForeignExchangeRegisterRow(
                new ForeignCurrencyObligationId("receivable-usd-001"),
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-03"),
                new AccountCode("foreign-receivable"),
                amount("USD", "10000"),
                amount("EUR", "9200"),
                Optional.of(LocalDate.parse("2026-07-03")),
                Optional.of(amount("EUR", "9500")),
                Optional.of(amount("EUR", "300")),
                Optional.of(true))));
  }

  private static MonetaryAmount amount(String currencyCode, String minorUnits) {
    return new MonetaryAmount(currencyCode, minorUnits);
  }
}
