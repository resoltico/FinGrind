package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct mapper coverage for standalone tax-row decoding branches. */
class SqliteTaxMapperTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void mapper_decodesStandaloneRegistrationAndTaxCodeRows() {
    Path bookPath = tempDirectory.resolve("tax-mapper-standalone.sqlite");
    withStandaloneDatabase(
        bookAccess(bookPath),
        database -> {
          try (SqliteNativeStatement row =
                  database.prepare("select 'value' as present_text, null as absent_text");
              SqliteNativeStatement taxCodeRow =
                  database.prepare(
                      """
                      select
                          'vat-standard-sale',
                          'VAT Standard Sale',
                          210000,
                          'EXCLUSIVE',
                          'OUTPUT_SALE'
                      """)) {
            assertEquals(SqliteNativeResultCode.code("ROW"), row.step());
            assertEquals(Optional.of("value"), SqliteTaxMapper.optionalText(row, 0));
            assertEquals(Optional.empty(), SqliteTaxMapper.optionalText(row, 1));

            assertEquals(SqliteNativeResultCode.code("ROW"), taxCodeRow.step());
            TaxCodeDefinition taxCode = SqliteTaxMapper.taxCodeDefinition(taxCodeRow);
            assertEquals(new TaxCode("vat-standard-sale"), taxCode.taxCode());
            assertEquals(new TaxCodeName("VAT Standard Sale"), taxCode.taxCodeName());
            assertEquals(new TaxRate(210_000), taxCode.rate());
            assertEquals(TaxInclusionMode.EXCLUSIVE, taxCode.inclusionMode());
            assertEquals(TaxApplicationKind.OUTPUT_SALE, taxCode.applicationKind());
          }

          assertEquals(
              new TaxRegistrationId("vat-lv"),
              SqliteTaxMapper.declaredTaxRegistration(
                      new SqliteTaxMapper.TaxRegistrationCoreRow(
                          "vat-lv",
                          "Latvia VAT",
                          "LV",
                          Optional.of("LV40001234567"),
                          "2100",
                          "1300",
                          "MONTHLY",
                          20,
                          "2026-04-08T10:15:30Z"),
                      List.of(
                          new TaxCodeDefinition(
                              new TaxCode("vat-standard-sale"),
                              new TaxCodeName("VAT Standard Sale"),
                              new TaxRate(210_000),
                              TaxInclusionMode.EXCLUSIVE,
                              TaxApplicationKind.OUTPUT_SALE)))
                  .taxRegistrationId());
        });
  }
}
