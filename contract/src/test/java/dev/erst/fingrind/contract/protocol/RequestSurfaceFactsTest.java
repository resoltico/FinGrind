package dev.erst.fingrind.contract.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.core.AccountClassificationReachability;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the canonical request-surface fact owner. */
class RequestSurfaceFactsTest {
  @Test
  void requestSurfacePublishesStableWireVocabularyAndCanonicalLookups() {
    RequestSurfaceFacts facts = ProtocolCatalog.domain().requestSurface();

    assertEquals(List.of("enumerated", "pattern-only"), SourceDocumentTypePolicyMode.wireValues());
    assertEquals(
        List.of("ranged-filter", "bounded-period", "as-of-date"),
        TemporalScopeArchetype.wireValues());
    assertEquals(
        SourceDocumentTypePolicyMode.ENUMERATED,
        facts
            .evidenceProfile(
                facts.journalRecipe(JournalRecipeKind.CASH_REVENUE).evidenceProfileId())
            .sourceDocumentTypes()
            .mode());
    assertEquals(
        SourceDocumentTypePolicyMode.PATTERN_ONLY,
        facts
            .evidenceProfile(
                facts
                    .postEntryKind(BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION)
                    .evidenceProfileId())
            .sourceDocumentTypes()
            .mode());
    assertEquals(
        SourceDocumentTypePolicyMode.PATTERN_ONLY,
        facts
            .evidenceProfile(
                facts
                    .postEntryKind(BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION)
                    .evidenceProfileId())
            .sourceDocumentTypes()
            .mode());
    assertEquals(
        List.of(ProtocolOptions.PERIOD_START, ProtocolOptions.PERIOD_END),
        facts.temporalScope(TemporalScopeArchetype.BOUNDED_PERIOD).optionNames());
    assertEquals(
        TemporalScopeArchetype.BOUNDED_PERIOD,
        facts.temporalScopeFor(OperationId.TRANSFER_PERIOD_RESULT).archetype());
    assertEquals("As of", facts.temporalScopeFor(OperationId.FINANCIAL_POSITION).summaryLabel());
    assertEquals(
        "One explicit closed reporting window. Both boundaries must be supplied, and neither boundary falls back to book start or the current book horizon.",
        facts.temporalScope(TemporalScopeArchetype.BOUNDED_PERIOD).boundarySemantics());
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
                    "Accepted source-document types."));
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
                    "Pattern-only source-document types."));
    assertEquals(
        "acceptedValues must be empty when source-document types are pattern-only.",
        patternOnlyAcceptedValues.getMessage());

    IllegalArgumentException invalidEvidenceFacts =
        assertThrows(
            IllegalArgumentException.class,
            () -> new RequestSurfaceFacts.EvidenceRequirementFacts("Evidence", 0, List.of("id")));
    assertEquals("minimumSourceDocuments must be at least one.", invalidEvidenceFacts.getMessage());

    IllegalArgumentException invalidEvidenceDescriptor =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new ContractRequestShapes.EvidenceRequirementDescriptor(
                    "Evidence", 0, List.of("id")));
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
                    "One point-in-time effective-date cutoff.",
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

    IllegalArgumentException nonDeclarableOperationalReachability =
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
    assertEquals(
        "Non-declarable reachability cells must not report any reachable write path.",
        nonDeclarableOperationalReachability.getMessage());

    IllegalArgumentException nonDeclarableReversalReachability =
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
    assertEquals(
        "Non-declarable reachability cells must not report any reachable write path.",
        nonDeclarableReversalReachability.getMessage());

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
  }

  @Test
  void requestSurfaceFactsRejectDuplicateFactsAndReportMissingLookups() {
    IllegalArgumentException duplicateEntryKinds =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(
                        postEntryKindFacts(BookkeepingEntryKind.JOURNAL),
                        postEntryKindFacts(BookkeepingEntryKind.JOURNAL)),
                    List.of(journalRecipeFacts(JournalRecipeKind.CASH_REVENUE)),
                    List.of(evidenceProfileFacts("cash-revenue")),
                    List.of(),
                    evidenceFacts(),
                    List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE))));
    assertEquals(
        "Duplicate request-surface facts for entryKind JOURNAL.", duplicateEntryKinds.getMessage());

    IllegalArgumentException duplicateTemporalScopes =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(postEntryKindFacts(BookkeepingEntryKind.JOURNAL)),
                    List.of(journalRecipeFacts(JournalRecipeKind.CASH_REVENUE)),
                    List.of(evidenceProfileFacts("cash-revenue")),
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

    IllegalArgumentException duplicateRecipeKinds =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(postEntryKindFacts(BookkeepingEntryKind.JOURNAL)),
                    List.of(
                        journalRecipeFacts(JournalRecipeKind.CASH_REVENUE),
                        journalRecipeFacts(JournalRecipeKind.CASH_REVENUE)),
                    List.of(evidenceProfileFacts("cash-revenue")),
                    List.of(),
                    evidenceFacts(),
                    List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE))));
    assertEquals(
        "Duplicate journal recipe facts for recipeKind CASH_REVENUE.",
        duplicateRecipeKinds.getMessage());

    IllegalArgumentException duplicateEvidenceProfiles =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(postEntryKindFacts(BookkeepingEntryKind.JOURNAL)),
                    List.of(journalRecipeFacts(JournalRecipeKind.CASH_REVENUE)),
                    List.of(
                        evidenceProfileFacts("cash-revenue"), evidenceProfileFacts("cash-revenue")),
                    List.of(),
                    evidenceFacts(),
                    List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE))));
    assertEquals(
        "Duplicate evidence profile facts for profileId cash-revenue.",
        duplicateEvidenceProfiles.getMessage());

    IllegalArgumentException duplicateReachabilityCells =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new RequestSurfaceFacts(
                    List.of(postEntryKindFacts(BookkeepingEntryKind.JOURNAL)),
                    List.of(journalRecipeFacts(JournalRecipeKind.CASH_REVENUE)),
                    List.of(evidenceProfileFacts("cash-revenue")),
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
                    List.of(postEntryKindFacts(BookkeepingEntryKind.JOURNAL)),
                    List.of(journalRecipeFacts(JournalRecipeKind.CASH_REVENUE)),
                    List.of(evidenceProfileFacts("cash-revenue")),
                    List.of(),
                    evidenceFacts(),
                    List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
                    List.of(
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE),
                        commandTemporalScopeFacts(
                            OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.BOUNDED_PERIOD))));
    assertEquals(
        "Duplicate temporal-scope facts for command "
            + OperationId.ACCOUNT_BALANCE.wireName()
            + ".",
        duplicateCommandMappings.getMessage());

    RequestSurfaceFacts limitedFacts =
        new RequestSurfaceFacts(
            List.of(postEntryKindFacts(BookkeepingEntryKind.JOURNAL)),
            List.of(journalRecipeFacts(JournalRecipeKind.CASH_REVENUE)),
            List.of(evidenceProfileFacts("cash-revenue")),
            List.of(),
            evidenceFacts(),
            List.of(temporalScopeFacts(TemporalScopeArchetype.AS_OF_DATE)),
            List.of(
                commandTemporalScopeFacts(
                    OperationId.ACCOUNT_BALANCE, TemporalScopeArchetype.AS_OF_DATE)));

    assertEquals(
        "No posting request facts are registered for OPEN_ACCOUNTING_POSITION.",
        assertThrows(
                IllegalStateException.class,
                () -> limitedFacts.postEntryKind(BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION))
            .getMessage());
    assertEquals(
        "No journal recipe facts are registered for CASH_EXPENSE.",
        assertThrows(
                IllegalStateException.class,
                () -> limitedFacts.journalRecipe(JournalRecipeKind.CASH_EXPENSE))
            .getMessage());
    assertEquals(
        "No evidence profile facts are registered for opening-balance.",
        assertThrows(
                IllegalStateException.class, () -> limitedFacts.evidenceProfile("opening-balance"))
            .getMessage());
    assertEquals(
        "No temporal-scope facts are registered for BOUNDED_PERIOD.",
        assertThrows(
                IllegalStateException.class,
                () -> limitedFacts.temporalScope(TemporalScopeArchetype.BOUNDED_PERIOD))
            .getMessage());
    assertEquals(
        "No temporal-scope facts are registered for " + OperationId.PERIOD_SUMMARY.wireName() + ".",
        assertThrows(
                IllegalStateException.class,
                () -> limitedFacts.temporalScopeFor(OperationId.PERIOD_SUMMARY))
            .getMessage());
  }

  private static RequestSurfaceFacts.PostEntryKindFacts postEntryKindFacts(
      BookkeepingEntryKind entryKind) {
    return new RequestSurfaceFacts.PostEntryKindFacts(
        entryKind,
        List.of("effectiveDate"),
        List.of("journalEntry"),
        "cash-revenue",
        "Posting request semantics.");
  }

  private static RequestSurfaceFacts.JournalRecipeFacts journalRecipeFacts(
      JournalRecipeKind recipeKind) {
    return new RequestSurfaceFacts.JournalRecipeFacts(
        recipeKind,
        List.of("effectiveDate", "recipeKind"),
        List.of("lines"),
        "cash-revenue",
        "Recipe semantics.");
  }

  private static RequestSurfaceFacts.EvidenceProfileFacts evidenceProfileFacts(String profileId) {
    return new RequestSurfaceFacts.EvidenceProfileFacts(
        profileId,
        new RequestSurfaceFacts.SourceDocumentTypeFacts(
            SourceDocumentTypePolicyMode.ENUMERATED,
            List.of("cash-receipt"),
            "Accepted source-document types."),
        "Evidence profile semantics.");
  }

  private static RequestSurfaceFacts.EvidenceRequirementFacts evidenceFacts() {
    return new RequestSurfaceFacts.EvidenceRequirementFacts(
        "Every posting request must retain one source document.",
        1,
        List.of(
            "sourceDocumentType",
            "sourceDocumentId",
            "sourceDocumentLineId",
            "counterpartyId",
            "counterpartyName",
            "narrative"));
  }

  private static RequestSurfaceFacts.TemporalScopeFacts temporalScopeFacts(
      TemporalScopeArchetype archetype) {
    return new RequestSurfaceFacts.TemporalScopeFacts(
        archetype,
        List.of("--effective-date-as-of"),
        "As of",
        "As of",
        "As of",
        "One point-in-time effective-date cutoff.",
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
