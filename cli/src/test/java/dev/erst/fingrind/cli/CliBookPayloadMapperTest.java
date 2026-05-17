package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountingBasis;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityForm;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import dev.erst.fingrind.core.OwnerModel;
import dev.erst.fingrind.core.PercentageRate;
import dev.erst.fingrind.core.ReportingObligationStatus;
import dev.erst.fingrind.core.TaxCode;
import dev.erst.fingrind.core.TaxCodeDefinition;
import dev.erst.fingrind.core.TaxCodeName;
import dev.erst.fingrind.core.TaxFilingFrequency;
import dev.erst.fingrind.core.TaxJurisdictionCode;
import dev.erst.fingrind.core.TaxPricingMode;
import dev.erst.fingrind.core.TaxProfile;
import dev.erst.fingrind.core.TaxRecoverability;
import dev.erst.fingrind.core.TaxRegistration;
import dev.erst.fingrind.core.TaxRegistrationId;
import dev.erst.fingrind.core.TaxRegistrationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused regression tests for {@link CliBookPayloadMapper}. */
class CliBookPayloadMapperTest extends FinGrindCliTestSupport {
  @Test
  void bookAndPostingContextPayloads_mapIdentityAndSelectedFilters() {
    CliBookQueryJsonModels.BookContextPayload bookContext =
        CliBookPayloadMapper.bookContextPayload(bookIdentity());
    CliBookQueryJsonModels.PostingQueryContextPayload unbounded =
        CliBookPayloadMapper.postingQueryContextPayload(bookIdentity(), null, null, null);
    CliBookQueryJsonModels.PostingQueryContextPayload filtered =
        CliBookPayloadMapper.postingQueryContextPayload(
            bookIdentity(),
            new AccountCode("1000"),
            LocalDate.parse("2026-04-01"),
            LocalDate.parse("2026-04-30"));

    assertEquals("Acme Studio", bookContext.bookIdentity().entityName());
    assertEquals("COMPANY", bookContext.bookIdentity().entityForm());
    assertEquals(List.of(), bookContext.bookIdentity().businessActivityTags());

    assertNull(unbounded.accountCodeFilter());
    assertNull(unbounded.effectiveDateFrom());
    assertEquals("book-start", unbounded.effectiveDateFromMeaning());
    assertNull(unbounded.effectiveDateTo());
    assertEquals("latest-committed-posting", unbounded.effectiveDateToMeaning());

    assertEquals("1000", filtered.accountCodeFilter());
    assertEquals("2026-04-01", filtered.effectiveDateFrom());
    assertEquals("selected-date", filtered.effectiveDateFromMeaning());
    assertEquals("2026-04-30", filtered.effectiveDateTo());
    assertEquals("selected-date", filtered.effectiveDateToMeaning());
  }

  @Test
  void bookIdentityPayload_mapsBusinessActivityTagsWhenPresent() {
    BookIdentity taggedIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Acme Studio"),
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
                TaxRegistrationStatus.UNSPECIFIED,
                List.of(
                    new BusinessActivityTag("translation-services"),
                    new BusinessActivityTag("platform-sales"))),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingBasis.ACCRUAL,
            TaxProfile.empty());

    var payload = CliBookPayloadMapper.bookIdentityPayload(taggedIdentity);

    assertEquals(List.of("translation-services", "platform-sales"), payload.businessActivityTags());
    assertEquals(List.of(), payload.taxProfile().registrations());
    assertEquals(List.of(), payload.taxProfile().taxCodeDefinitions());
  }

  @Test
  void bookIdentityPayload_mapsStructuredTaxProfile() {
    BookIdentity registeredIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Registered Studio"),
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
                TaxRegistrationStatus.REGISTERED,
                List.of()),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingBasis.ACCRUAL,
            new TaxProfile(
                List.of(
                    new TaxRegistration(
                        new TaxJurisdictionCode("LV"),
                        new TaxRegistrationId("LV123456789"),
                        TaxFilingFrequency.MONTHLY)),
                List.of(
                    new TaxCodeDefinition(
                        new TaxCode("VAT21"),
                        new TaxCodeName("Standard VAT"),
                        new TaxJurisdictionCode("LV"),
                        new PercentageRate(2100),
                        TaxPricingMode.EXCLUSIVE,
                        TaxRecoverability.FULLY_RECOVERABLE,
                        new AccountCode("2100"),
                        Optional.of(new AccountCode("1300"))))));

    var payload = CliBookPayloadMapper.bookIdentityPayload(registeredIdentity);

    assertEquals("REGISTERED", payload.taxRegistrationStatus());
    assertEquals(1, payload.taxProfile().registrations().size());
    assertEquals("LV", payload.taxProfile().registrations().getFirst().jurisdictionCode());
    assertEquals(1, payload.taxProfile().taxCodeDefinitions().size());
    assertEquals("VAT21", payload.taxProfile().taxCodeDefinitions().getFirst().taxCode());
    assertEquals(2100, payload.taxProfile().taxCodeDefinitions().getFirst().rateBasisPoints());
  }

  @Test
  void bookIdentityPayload_preservesOptionalMissingReceivableAccountCode() {
    BookIdentity registeredIdentity =
        new BookIdentity(
            new EntityProfile(
                new BookEntityName("Registered Studio"),
                EntityForm.COMPANY,
                OwnerModel.MULTI_OWNER,
                ReportingObligationStatus.INTERNAL_MANAGEMENT_ONLY,
                TaxRegistrationStatus.REGISTERED,
                List.of()),
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"),
            AccountingBasis.ACCRUAL,
            new TaxProfile(
                List.of(
                    new TaxRegistration(
                        new TaxJurisdictionCode("LV"),
                        new TaxRegistrationId("LV123456789"),
                        TaxFilingFrequency.MONTHLY)),
                List.of(
                    new TaxCodeDefinition(
                        new TaxCode("VAT21"),
                        new TaxCodeName("Standard VAT"),
                        new TaxJurisdictionCode("LV"),
                        new PercentageRate(2100),
                        TaxPricingMode.EXCLUSIVE,
                        TaxRecoverability.FULLY_RECOVERABLE,
                        new AccountCode("2100"),
                        Optional.empty()))));

    var payload = CliBookPayloadMapper.bookIdentityPayload(registeredIdentity);

    assertNull(payload.taxProfile().taxCodeDefinitions().getFirst().receivableAccountCode());
  }
}
