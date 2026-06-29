package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Focused tax-surface protocol coverage for field inventories and disjoint fact rules. */
class TaxProtocolSurfaceTest {
  @Test
  void taxRegistrationFieldCatalogs_publishStableWireOrder() {
    assertEquals(
        List.of(
            "taxRegistrationId",
            "taxRegistrationName",
            "jurisdiction",
            "registrationNumber",
            "payableAccountCode",
            "recoverableAccountCode",
            "obligationFrequency",
            "dueDaysAfterPeriodEnd",
            "taxCodes"),
        ProtocolTaxRegistrationFields.topLevelFields());
    assertEquals(
        List.of(
            "taxCode", "taxCodeName", "ratePartsPerMillion", "inclusionMode", "applicationKind"),
        ProtocolTaxRegistrationFields.taxCodeFields());
  }

  @Test
  void bookkeepingEntryKindFacts_rejectOverlappingFieldBuckets() {
    assertEquals(
        "requiredTopLevelFields, optionalTopLevelFields, and forbiddenTopLevelFields must be disjoint.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new RequestSurfaceFacts.BookkeepingEntryKindFacts(
                        BookkeepingEntryKind.SALE,
                        List.of("effectiveDate"),
                        List.of("effectiveDate"),
                        List.of("lines"),
                        List.of("sourceDocumentId", "sourceDocumentType", "documentDate"),
                        enumeratedSourceDocumentTypes(),
                        "Sale semantics."))
            .getMessage());
    assertEquals(
        "requiredTopLevelFields, optionalTopLevelFields, and forbiddenTopLevelFields must be disjoint.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new RequestSurfaceFacts.BookkeepingEntryKindFacts(
                        BookkeepingEntryKind.SALE,
                        List.of("effectiveDate"),
                        List.of(),
                        List.of("effectiveDate"),
                        List.of("sourceDocumentId", "sourceDocumentType", "documentDate"),
                        enumeratedSourceDocumentTypes(),
                        "Sale semantics."))
            .getMessage());
    assertEquals(
        "requiredTopLevelFields, optionalTopLevelFields, and forbiddenTopLevelFields must be disjoint.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new RequestSurfaceFacts.BookkeepingEntryKindFacts(
                        BookkeepingEntryKind.SALE,
                        List.of("effectiveDate"),
                        List.of("tax"),
                        List.of("tax"),
                        List.of("sourceDocumentId", "sourceDocumentType", "documentDate"),
                        enumeratedSourceDocumentTypes(),
                        "Sale semantics."))
            .getMessage());
  }

  @Test
  void requestSurfaceFactsValidation_rejectsRepeatedAcceptedOptionalFields() {
    IllegalArgumentException repeatedAcceptedField =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                RequestSurfaceFactsValidation.requireUniqueEntryKinds(
                    List.of(
                        new RequestSurfaceFacts.BookkeepingEntryKindFacts(
                            BookkeepingEntryKind.SALE,
                            List.of("effectiveDate"),
                            List.of("tax", "tax"),
                            List.of("lines"),
                            List.of("sourceDocumentId", "sourceDocumentType", "documentDate"),
                            enumeratedSourceDocumentTypes(),
                            "Sale semantics."))));

    assertEquals(
        "Entry kind SALE repeats accepted top-level field tax.",
        repeatedAcceptedField.getMessage());
  }

  private static RequestSurfaceFacts.SourceDocumentTypeFacts enumeratedSourceDocumentTypes() {
    return new RequestSurfaceFacts.SourceDocumentTypeFacts(
        SourceDocumentTypePolicyMode.ENUMERATED,
        List.of("cash-receipt"),
        "Accepted source-document types.",
        "cash-receipt");
  }
}
