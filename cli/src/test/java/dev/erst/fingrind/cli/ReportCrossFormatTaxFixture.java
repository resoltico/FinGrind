package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationCodeSummary;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Sample tax fixtures used by the cross-format equivalence tests. */
final class ReportCrossFormatTaxFixture {
  private ReportCrossFormatTaxFixture() {}

  static TaxObligationReport sampleTaxObligationReport() {
    return new TaxObligationReport(
        CliFixtureSupport.bookIdentity(),
        sampleTaxRegistration(),
        new ReportingPeriod(LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")),
        LocalDate.parse("2026-05-20"),
        List.of(
            new TaxObligationCodeSummary(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                TaxApplicationKind.OUTPUT_SALE,
                3,
                new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "15000"),
                new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "3150"),
                new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "18150")),
            new TaxObligationCodeSummary(
                new TaxCode("vat-standard-purchase"),
                new TaxCodeName("VAT Standard Purchase"),
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                2,
                new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "10000"),
                new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "2100"),
                new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "12100"))),
        new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "3150"),
        new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "2100"),
        new dev.erst.fingrind.contract.bookkeeping.SignedMonetaryAmount("EUR", "1200"),
        new MonetaryAmount("EUR", "1050"),
        new MonetaryAmount("EUR", "0"));
  }

  private static DeclaredTaxRegistration sampleTaxRegistration() {
    return new DeclaredTaxRegistration(
        new TaxRegistrationId("vat-lv"),
        new TaxRegistrationName("Latvia VAT"),
        new TaxJurisdiction("LV"),
        new TaxRegistrationNumber("LV40001234567"),
        new AccountCode("2100"),
        new AccountCode("1300"),
        TaxObligationFrequency.MONTHLY,
        20,
        List.of(
            new TaxCodeDefinition(
                new TaxCode("vat-standard-sale"),
                new TaxCodeName("VAT Standard Sale"),
                new TaxRate(210_000),
                TaxInclusionMode.EXCLUSIVE,
                TaxApplicationKind.OUTPUT_SALE)),
        Instant.parse("2026-04-17T10:20:30Z"));
  }
}
