package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.List;

/** Canonical valid JSON transport fixtures shared by focused model validation tests. */
final class CliJsonValidationFixtures {
  private CliJsonValidationFixtures() {}

  static CliBookQueryJsonModels.DeclaredAccountPayload accountPayload(
      String accountCode, String accountName) {
    return new CliBookQueryJsonModels.DeclaredAccountPayload(
        accountCode,
        accountName,
        "ASSET",
        "POSTABLE",
        null,
        null,
        "CURRENT_ASSET",
        null,
        null,
        null,
        "DEBIT",
        true,
        "2026-05-14T10:00:00Z");
  }

  static CliTaxJsonModels.DeclaredTaxRegistrationPayload taxRegistrationPayload() {
    return new CliTaxJsonModels.DeclaredTaxRegistrationPayload(
        "vat-lv",
        "Latvian VAT",
        "LV",
        null,
        "2100",
        "1300",
        "MONTHLY",
        20,
        List.of(
            new CliTaxJsonModels.DeclaredTaxCodePayload(
                "VAT21", "VAT 21%", 210_000, "EXCLUSIVE", "OUTPUT")),
        "2026-05-14T10:00:00Z");
  }

  static CliBookQueryJsonModels.PostingSummaryPayload postingSummaryPayload(String postingId) {
    return new CliBookQueryJsonModels.PostingSummaryPayload(
        postingId,
        "STANDARD",
        "SALE_SETTLED",
        "ACTIVE",
        null,
        null,
        null,
        "2026-05-14",
        "2026-05-14T10:00:00Z",
        MonetaryAmount.of(dev.erst.fingrind.core.Money.parse("EUR", "0.00")),
        MonetaryAmount.of(dev.erst.fingrind.core.Money.parse("EUR", "10.00")),
        List.of("4000"),
        List.of("invoice-1"),
        List.of());
  }
}
