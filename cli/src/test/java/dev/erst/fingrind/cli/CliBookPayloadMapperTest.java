package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookEntityName;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.BusinessActivityTag;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.EntityProfile;
import dev.erst.fingrind.core.FiscalYearStart;
import java.time.LocalDate;
import java.util.List;
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
    assertEquals(
        "country-agnostic-bookkeeping-kernel",
        bookContext.bookIdentity().accountingKernelProfile());
    assertEquals(
        List.of("translation-services"), bookContext.bookIdentity().businessActivityTags());

    assertNull(unbounded.accountCodeFilter());
    assertNull(unbounded.effectiveDateFrom());
    assertEquals("book-start", unbounded.effectiveDateFromMeaning());
    assertNull(unbounded.effectiveDateTo());
    assertEquals("current-book-horizon", unbounded.effectiveDateToMeaning());

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
                List.of(
                    new BusinessActivityTag("translation-services"),
                    new BusinessActivityTag("platform-sales"))),
            dev.erst.fingrind.core.AccountingKernelProfiles.COUNTRY_AGNOSTIC_BOOKKEEPING_KERNEL,
            CurrencyUnit.of("EUR"),
            FiscalYearStart.parse("01-01"));

    var payload = CliBookPayloadMapper.bookIdentityPayload(taggedIdentity);

    assertEquals("country-agnostic-bookkeeping-kernel", payload.accountingKernelProfile());
    assertEquals(List.of("translation-services", "platform-sales"), payload.businessActivityTags());
  }

  @Test
  void evidencePayload_mapsApprovalEvidence() {
    CliBookQueryJsonModels.AccountingEvidencePayload payload =
        CliBookPayloadMapper.evidencePayload(CliFixtureSupport.accountingEvidenceWithApproval("1"));

    assertEquals(1, payload.sourceDocuments().size());
    assertEquals("document-1", payload.sourceDocuments().get(0).sourceDocumentId());
    assertEquals("cash-receipt", payload.sourceDocuments().get(0).sourceDocumentType());
    assertEquals(1, payload.approvals().size());
    assertEquals("approval-1", payload.approvals().get(0).approvalId());
    assertEquals("manager-signoff", payload.approvals().get(0).approvalType());
  }

  @Test
  void postingSummaryPayload_mapsApprovalIdsWhenPresent() {
    CliBookQueryJsonModels.PostingSummaryPayload payload =
        CliBookPayloadMapper.postingSummaryPayload(
            CliResponseWriterTestSupport.postingFactWithApproval());

    assertEquals(List.of("document-idem-1"), payload.sourceDocumentIds());
    assertEquals(List.of("approval-idem-1"), payload.approvalIds());
  }
}
