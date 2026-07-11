package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the canonical request-surface fact owner. */
class RequestSurfaceFactsTest {
  @Test
  void requestSurfacePublishesStableLookupsAndSourceDocumentPolicies() {
    RequestSurfaceFacts facts = ProtocolCatalog.domain().requestSurface();

    assertEquals(List.of("enumerated", "pattern-only"), SourceDocumentTypePolicyMode.wireValues());
    assertEquals(
        List.of(
            "ranged-filter",
            "bounded-period",
            "through-date",
            "fiscal-year-label",
            "as-of-date",
            "inventory-as-of-date"),
        TemporalScopeArchetype.wireValues());
    assertEquals(
        SourceDocumentTypePolicyMode.ENUMERATED,
        facts.bookkeepingEntryKind(BookkeepingEntryKind.SALE_SETTLED).sourceDocumentTypes().mode());
    assertEquals(
        List.of("cash-receipt", "bank-deposit", "card-settlement"),
        facts
            .bookkeepingEntryKind(BookkeepingEntryKind.SALE_SETTLED)
            .sourceDocumentTypes()
            .acceptedValues());
    assertEquals(
        "cash-receipt",
        facts
            .bookkeepingEntryKind(BookkeepingEntryKind.SALE_SETTLED)
            .sourceDocumentTypes()
            .scaffoldValue());
    assertEquals(
        SourceDocumentTypePolicyMode.PATTERN_ONLY,
        facts
            .bookkeepingEntryKind(BookkeepingEntryKind.OPENING_POSITION)
            .sourceDocumentTypes()
            .mode());
    assertEquals(
        List.of(),
        facts
            .bookkeepingEntryKind(BookkeepingEntryKind.OPENING_POSITION)
            .sourceDocumentTypes()
            .acceptedValues());
    assertEquals(
        "opening-balance-support",
        facts
            .bookkeepingEntryKind(BookkeepingEntryKind.OPENING_POSITION)
            .sourceDocumentTypes()
            .scaffoldValue());
    assertEquals(
        List.of(ProtocolOptions.PERIOD_START, ProtocolOptions.PERIOD_END),
        facts.temporalScope(TemporalScopeArchetype.BOUNDED_PERIOD).optionNames());
    assertEquals(
        TemporalScopeArchetype.THROUGH_DATE,
        facts.temporalScopeFor(OperationId.INTERIM_RESULT_SWEEP).archetype());
    assertEquals(
        TemporalScopeArchetype.FISCAL_YEAR_LABEL,
        facts.temporalScopeFor(OperationId.FISCAL_YEAR_CLOSE).archetype());
    assertEquals(
        "Inclusive through date. FinGrind derives the contiguous sweep window from book start in the selected book or, after a sweep is recorded, from the live transferred-through horizon.",
        facts.temporalScopeFor(OperationId.INTERIM_RESULT_SWEEP).boundarySemantics());
    assertEquals("As of", facts.temporalScopeFor(OperationId.FINANCIAL_POSITION).summaryLabel());
    assertEquals(
        AccountClassificationReachability.currentKernel().stream()
            .map(
                cell ->
                    new RequestSurfaceFacts.ReachabilityCellFacts(
                        cell.classificationFamily(),
                        cell.accountType(),
                        cell.classification(),
                        cell.declarable(),
                        cell.openingReachable(),
                        cell.operationalJournalReachable(),
                        cell.reversalReachable()))
            .toList(),
        facts.reachabilityMatrix());
  }

  @Test
  void requestSurfaceFactsRejectInvalidDescriptorShapes() {
    IllegalArgumentException emptyEnumeratedTypes =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts.SourceDocumentTypeFacts(
                    SourceDocumentTypePolicyMode.ENUMERATED,
                    List.of(),
                    "Accepted source-document types.",
                    "cash-receipt"));
    assertEquals(
        "acceptedValues must not be empty when source-document types are enumerated.",
        emptyEnumeratedTypes.getMessage());

    IllegalArgumentException patternOnlyAcceptedValues =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts.SourceDocumentTypeFacts(
                    SourceDocumentTypePolicyMode.PATTERN_ONLY,
                    List.of("invoice"),
                    "Pattern-only source-document types.",
                    "working-note"));
    assertEquals(
        "acceptedValues must be empty when source-document types are pattern-only.",
        patternOnlyAcceptedValues.getMessage());

    IllegalArgumentException invalidPostEntryFacts =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts.BookkeepingEntryKindFacts(
                    BookkeepingEntryKind.SALE_SETTLED,
                    List.of("effectiveDate"),
                    List.of(),
                    List.of("lines"),
                    List.of(),
                    enumeratedSourceDocumentTypes(),
                    "Sale semantics."));
    assertEquals(
        "requiredSourceDocumentFields must not be empty.", invalidPostEntryFacts.getMessage());

    IllegalArgumentException invalidEvidenceFacts =
        assertThrows(
            IllegalArgumentException.class,
            () -> new RequestSurfaceFacts.EvidenceRequirementFacts("Evidence", 0));
    assertEquals("minimumSourceDocuments must be at least one.", invalidEvidenceFacts.getMessage());

    IllegalArgumentException invalidEvidenceDescriptor =
        assertThrows(
            IllegalArgumentException.class,
            () -> new ContractRequestShapes.EvidenceRequirementDescriptor("Evidence", 0));
    assertEquals(
        "minimumSourceDocuments must be at least one.", invalidEvidenceDescriptor.getMessage());

    IllegalArgumentException emptyOptionNames =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts.TemporalScopeFacts(
                    TemporalScopeArchetype.AS_OF_DATE,
                    List.of(),
                    "As of",
                    "As of",
                    "As of",
                    "Point-in-time effective-date cutoff.",
                    "selected-date",
                    "book-start",
                    "current-book-horizon",
                    "latest-posting-effective-date",
                    "no-postings"));
    assertEquals("optionNames must not be empty.", emptyOptionNames.getMessage());

    IllegalArgumentException nonDeclarableReachability =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts.ReachabilityCellFacts(
                    "financial-position",
                    dev.erst.fingrind.core.AccountType.ASSET,
                    "cash",
                    false,
                    true,
                    false,
                    false));
    assertEquals(
        "Non-declarable reachability cells must not report any reachable write path.",
        nonDeclarableReachability.getMessage());

    assertFalse(
        new RequestSurfaceFacts.ReachabilityCellFacts(
                "financial-position",
                dev.erst.fingrind.core.AccountType.ASSET,
                "cash",
                false,
                false,
                false,
                false)
            .declarable());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RequestSurfaceFacts.ReachabilityCellFacts(
                "financial-position",
                dev.erst.fingrind.core.AccountType.ASSET,
                "cash",
                false,
                false,
                true,
                false));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new RequestSurfaceFacts.ReachabilityCellFacts(
                "financial-position",
                dev.erst.fingrind.core.AccountType.ASSET,
                "cash",
                false,
                false,
                false,
                true));
  }

  @Test
  void requestSurfaceFactsRejectDuplicateFactsAndReportMissingLookups() {
    IllegalArgumentException duplicateEntryKinds =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(
                        postEntryKindFacts(BookkeepingEntryKind.SALE_SETTLED),
                        postEntryKindFacts(BookkeepingEntryKind.SALE_SETTLED)),
                    List.of(),
                    evidenceFacts(),
                    List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE))));
    assertEquals(
        "Duplicate request-surface facts for entryKind SALE_SETTLED.",
        duplicateEntryKinds.getMessage());

    IllegalArgumentException duplicateTemporalScopes =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(postEntryKindFacts(BookkeepingEntryKind.SALE_SETTLED)),
                    List.of(),
                    evidenceFacts(),
                    List.of(
                        temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE),
                        temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE))));
    assertEquals(
        "Duplicate temporal-scope facts for archetype AS_OF_DATE.",
        duplicateTemporalScopes.getMessage());

    IllegalArgumentException duplicateReachabilityCells =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(postEntryKindFacts(BookkeepingEntryKind.SALE_SETTLED)),
                    List.of(
                        new RequestSurfaceFacts.ReachabilityCellFacts(
                            "financial-position",
                            dev.erst.fingrind.core.AccountType.ASSET,
                            "cash",
                            true,
                            true,
                            true,
                            true),
                        new RequestSurfaceFacts.ReachabilityCellFacts(
                            "financial-position",
                            dev.erst.fingrind.core.AccountType.ASSET,
                            "cash",
                            true,
                            true,
                            true,
                            true)),
                    evidenceFacts(),
                    List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE))));
    assertEquals(
        "Duplicate reachability matrix facts for cell financial-position|ASSET|cash.",
        duplicateReachabilityCells.getMessage());

    IllegalArgumentException duplicateCommandMappings =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(postEntryKindFacts(BookkeepingEntryKind.SALE_SETTLED)),
                    List.of(),
                    evidenceFacts(),
                    List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE),
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.BOUNDED_PERIOD))));
    assertEquals(
        "Duplicate temporal-scope facts for command account-balance.",
        duplicateCommandMappings.getMessage());

    RequestSurfaceFacts limitedFacts =
        new RequestSurfaceFacts(
            List.of(postEntryKindFacts(BookkeepingEntryKind.SALE_SETTLED)),
            List.of(),
            evidenceFacts(),
            List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
            List.of(
                commandTemporalScopeFacts(
                    OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE)));

    assertEquals(
        "No bookkeeping-entry request facts are registered for OPENING_POSITION.",
        assertThrows(
                IllegalStateException.class,
                () -> limitedFacts.bookkeepingEntryKind(BookkeepingEntryKind.OPENING_POSITION))
            .getMessage());
    assertEquals(
        "No temporal-scope facts are registered for BOUNDED_PERIOD.",
        assertThrows(
                IllegalStateException.class,
                () -> limitedFacts.temporalScope(TemporalScopeArchetype.BOUNDED_PERIOD))
            .getMessage());
    assertEquals(
        "No temporal-scope facts are registered for period-summary.",
        assertThrows(
                IllegalStateException.class,
                () -> limitedFacts.temporalScopeFor(OperationId.PERIOD_SUMMARY))
            .getMessage());
  }

  private static RequestSurfaceFacts.BookkeepingEntryKindFacts postEntryKindFacts(
      BookkeepingEntryKind entryKind) {
    return new RequestSurfaceFacts.BookkeepingEntryKindFacts(
        entryKind,
        List.of("effectiveDate"),
        List.of(),
        List.of("lines"),
        List.of("sourceDocumentId", "sourceDocumentType", "documentDate"),
        enumeratedSourceDocumentTypes(),
        "Posting request semantics.");
  }

  private static RequestSurfaceFacts.SourceDocumentTypeFacts enumeratedSourceDocumentTypes() {
    return new RequestSurfaceFacts.SourceDocumentTypeFacts(
        SourceDocumentTypePolicyMode.ENUMERATED,
        List.of("cash-receipt"),
        "Accepted source-document types.",
        "cash-receipt");
  }

  private static RequestSurfaceFacts.EvidenceRequirementFacts evidenceFacts() {
    return new RequestSurfaceFacts.EvidenceRequirementFacts(
        "Every posting request must retain one source document.", 1);
  }

  private static RequestSurfaceFacts.TemporalScopeFacts temporalScopeFacts(
      TemporalScopeArchetype archetype) {
    return new RequestSurfaceFacts.TemporalScopeFacts(
        archetype,
        List.of("--effective-date-as-of"),
        "As of",
        "As of",
        "As of",
        "Point-in-time effective-date cutoff.",
        "selected-date",
        "book-start",
        "current-book-horizon",
        "latest-posting-effective-date",
        "no-postings");
  }

  private static RequestSurfaceFacts.CommandTemporalScopeFacts commandTemporalScopeFacts(
      OperationId operationId, TemporalScopeArchetype archetype) {
    return new RequestSurfaceFacts.CommandTemporalScopeFacts(operationId, archetype);
  }
}
