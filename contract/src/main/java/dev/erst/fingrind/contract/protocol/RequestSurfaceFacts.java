package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Objects;

/** Canonical request-surface facts shared by validation, discovery, help, and CLI text. */
public record RequestSurfaceFacts(
    List<PostEntryKindFacts> postEntryKinds,
    List<JournalRecipeFacts> journalRecipes,
    List<EvidenceProfileFacts> evidenceProfiles,
    List<ReachabilityCellFacts> reachabilityMatrix,
    EvidenceRequirementFacts postEntryEvidence,
    List<TemporalScopeFacts> temporalScopes,
    List<CommandTemporalScopeFacts> commandTemporalScopes) {
  /** Validates one request-surface fact bundle. */
  public RequestSurfaceFacts {
    postEntryKinds = ContractDescriptorValidation.copyList(postEntryKinds, "postEntryKinds");
    journalRecipes = ContractDescriptorValidation.copyList(journalRecipes, "journalRecipes");
    evidenceProfiles = ContractDescriptorValidation.copyList(evidenceProfiles, "evidenceProfiles");
    reachabilityMatrix =
        ContractDescriptorValidation.copyList(reachabilityMatrix, "reachabilityMatrix");
    postEntryEvidence =
        ContractDescriptorValidation.requireValue(postEntryEvidence, "postEntryEvidence");
    temporalScopes = ContractDescriptorValidation.copyList(temporalScopes, "temporalScopes");
    commandTemporalScopes =
        ContractDescriptorValidation.copyList(commandTemporalScopes, "commandTemporalScopes");
    RequestSurfaceFactsValidation.requireUniqueEntryKinds(postEntryKinds);
    RequestSurfaceFactsValidation.requireUniqueRecipeKinds(journalRecipes);
    RequestSurfaceFactsValidation.requireUniqueEvidenceProfiles(evidenceProfiles);
    RequestSurfaceFactsValidation.requireUniqueReachabilityCells(reachabilityMatrix);
    RequestSurfaceFactsValidation.requireUniqueTemporalArchetypes(temporalScopes);
    RequestSurfaceFactsValidation.requireUniqueTemporalScopeCommands(commandTemporalScopes);
  }

  /** Returns the canonical entry-kind facts for one posting request kind. */
  public PostEntryKindFacts postEntryKind(BookkeepingEntryKind entryKind) {
    BookkeepingEntryKind requiredEntryKind = Objects.requireNonNull(entryKind, "entryKind");
    return postEntryKinds.stream()
        .filter(facts -> facts.entryKind() == requiredEntryKind)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No posting request facts are registered for " + requiredEntryKind + "."));
  }

  /** Returns the canonical journal-recipe facts for one optional recipe kind. */
  public JournalRecipeFacts journalRecipe(JournalRecipeKind recipeKind) {
    JournalRecipeKind requiredRecipeKind = Objects.requireNonNull(recipeKind, "recipeKind");
    return journalRecipes.stream()
        .filter(facts -> facts.recipeKind() == requiredRecipeKind)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No journal recipe facts are registered for " + requiredRecipeKind + "."));
  }

  /** Returns the canonical evidence-profile facts for one profile id. */
  public EvidenceProfileFacts evidenceProfile(String profileId) {
    String requiredProfileId = ContractDescriptorValidation.requireText(profileId, "profileId");
    return evidenceProfiles.stream()
        .filter(facts -> facts.profileId().equals(requiredProfileId))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No evidence profile facts are registered for " + requiredProfileId + "."));
  }

  /** Returns the canonical temporal-scope facts for one archetype. */
  public TemporalScopeFacts temporalScope(TemporalScopeArchetype archetype) {
    TemporalScopeArchetype requiredArchetype = Objects.requireNonNull(archetype, "archetype");
    return temporalScopes.stream()
        .filter(facts -> facts.archetype() == requiredArchetype)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No temporal-scope facts are registered for " + requiredArchetype + "."));
  }

  /** Returns the canonical temporal-scope facts used by one command. */
  public TemporalScopeFacts temporalScopeFor(OperationId operationId) {
    OperationId requiredOperationId = Objects.requireNonNull(operationId, "operationId");
    TemporalScopeArchetype archetype =
        commandTemporalScopes.stream()
            .filter(facts -> facts.operationId() == requiredOperationId)
            .map(CommandTemporalScopeFacts::archetype)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No temporal-scope facts are registered for " + requiredOperationId + "."));
    return temporalScope(archetype);
  }

  /** Canonical per-entry-kind posting request facts. */
  public record PostEntryKindFacts(
      BookkeepingEntryKind entryKind,
      List<String> requiredTopLevelFields,
      List<String> forbiddenTopLevelFields,
      String evidenceProfileId,
      String semantics) {
    /** Validates one entry-kind request fact descriptor. */
    public PostEntryKindFacts {
      entryKind = ContractDescriptorValidation.requireValue(entryKind, "entryKind");
      requiredTopLevelFields =
          ContractDescriptorValidation.copyList(requiredTopLevelFields, "requiredTopLevelFields");
      forbiddenTopLevelFields =
          ContractDescriptorValidation.copyList(forbiddenTopLevelFields, "forbiddenTopLevelFields");
      evidenceProfileId =
          ContractDescriptorValidation.requireText(evidenceProfileId, "evidenceProfileId");
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
    }
  }

  /** Canonical optional journal-recipe facts for one recipe kind. */
  public record JournalRecipeFacts(
      JournalRecipeKind recipeKind,
      List<String> requiredTopLevelFields,
      List<String> forbiddenTopLevelFields,
      String evidenceProfileId,
      String semantics) {
    /** Validates one journal-recipe fact descriptor. */
    public JournalRecipeFacts {
      recipeKind = ContractDescriptorValidation.requireValue(recipeKind, "recipeKind");
      requiredTopLevelFields =
          ContractDescriptorValidation.copyList(requiredTopLevelFields, "requiredTopLevelFields");
      forbiddenTopLevelFields =
          ContractDescriptorValidation.copyList(forbiddenTopLevelFields, "forbiddenTopLevelFields");
      evidenceProfileId =
          ContractDescriptorValidation.requireText(evidenceProfileId, "evidenceProfileId");
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
    }
  }

  /** Canonical source-document type facts for one posting entry kind. */
  public record SourceDocumentTypeFacts(
      SourceDocumentTypePolicyMode mode, List<String> acceptedValues, String semantics) {
    /** Validates one source-document type fact bundle. */
    public SourceDocumentTypeFacts {
      mode = ContractDescriptorValidation.requireValue(mode, "mode");
      acceptedValues = ContractDescriptorValidation.copyList(acceptedValues, "acceptedValues");
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
      if (mode == SourceDocumentTypePolicyMode.ENUMERATED && acceptedValues.isEmpty()) {
        throw new IllegalArgumentException(
            "acceptedValues must not be empty when source-document types are enumerated.");
      }
      if (mode == SourceDocumentTypePolicyMode.PATTERN_ONLY && !acceptedValues.isEmpty()) {
        throw new IllegalArgumentException(
            "acceptedValues must be empty when source-document types are pattern-only.");
      }
    }
  }

  /** Canonical evidence-profile facts shared by direct journals and named recipes. */
  public record EvidenceProfileFacts(
      String profileId, SourceDocumentTypeFacts sourceDocumentTypes, String semantics) {
    /** Validates one evidence-profile fact bundle. */
    public EvidenceProfileFacts {
      profileId = ContractDescriptorValidation.requireText(profileId, "profileId");
      sourceDocumentTypes =
          ContractDescriptorValidation.requireValue(sourceDocumentTypes, "sourceDocumentTypes");
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
    }
  }

  /** Canonical account-taxonomy reachability facts for one declarable classification cell. */
  public record ReachabilityCellFacts(
      String classificationFamily,
      AccountType accountType,
      String classification,
      boolean declarable,
      boolean openingReachable,
      boolean operationalJournalReachable,
      boolean reversalReachable) {
    /** Validates one reachability matrix cell. */
    public ReachabilityCellFacts {
      classificationFamily =
          ContractDescriptorValidation.requireText(classificationFamily, "classificationFamily");
      accountType = ContractDescriptorValidation.requireValue(accountType, "accountType");
      classification = ContractDescriptorValidation.requireText(classification, "classification");
      if (!declarable && (openingReachable || operationalJournalReachable || reversalReachable)) {
        throw new IllegalArgumentException(
            "Non-declarable reachability cells must not report any reachable write path.");
      }
    }
  }

  /** Canonical evidence retention facts for posting requests. */
  public record EvidenceRequirementFacts(
      String description, int minimumSourceDocuments, List<String> requiredSourceDocumentFields) {
    /** Validates one evidence-requirement fact bundle. */
    public EvidenceRequirementFacts {
      description = ContractDescriptorValidation.requireText(description, "description");
      if (minimumSourceDocuments < 1) {
        throw new IllegalArgumentException("minimumSourceDocuments must be at least one.");
      }
      requiredSourceDocumentFields =
          ContractDescriptorValidation.copyList(
              requiredSourceDocumentFields, "requiredSourceDocumentFields");
    }
  }

  /** Canonical temporal-scope lexicon for one archetype. */
  public record TemporalScopeFacts(
      TemporalScopeArchetype archetype,
      List<String> optionNames,
      String summaryLabel,
      String lowerLabel,
      String upperLabel,
      String boundarySemantics,
      String selectedBoundaryMeaning,
      String omittedLowerBoundaryMeaning,
      String omittedUpperBoundaryMeaning,
      String resolvedUpperBoundaryMeaning,
      String emptyResultBoundaryMeaning) {
    /** Validates one temporal-scope lexicon. */
    public TemporalScopeFacts {
      archetype = ContractDescriptorValidation.requireValue(archetype, "archetype");
      optionNames = ContractDescriptorValidation.copyList(optionNames, "optionNames");
      summaryLabel = ContractDescriptorValidation.requireText(summaryLabel, "summaryLabel");
      lowerLabel = ContractDescriptorValidation.requireText(lowerLabel, "lowerLabel");
      upperLabel = ContractDescriptorValidation.requireText(upperLabel, "upperLabel");
      boundarySemantics =
          ContractDescriptorValidation.requireText(boundarySemantics, "boundarySemantics");
      selectedBoundaryMeaning =
          ContractDescriptorValidation.requireText(
              selectedBoundaryMeaning, "selectedBoundaryMeaning");
      omittedLowerBoundaryMeaning =
          ContractDescriptorValidation.requireText(
              omittedLowerBoundaryMeaning, "omittedLowerBoundaryMeaning");
      omittedUpperBoundaryMeaning =
          ContractDescriptorValidation.requireText(
              omittedUpperBoundaryMeaning, "omittedUpperBoundaryMeaning");
      resolvedUpperBoundaryMeaning =
          ContractDescriptorValidation.requireText(
              resolvedUpperBoundaryMeaning, "resolvedUpperBoundaryMeaning");
      emptyResultBoundaryMeaning =
          ContractDescriptorValidation.requireText(
              emptyResultBoundaryMeaning, "emptyResultBoundaryMeaning");
      if (optionNames.isEmpty()) {
        throw new IllegalArgumentException("optionNames must not be empty.");
      }
    }
  }

  /** Associates one command with its canonical temporal-scope archetype. */
  public record CommandTemporalScopeFacts(
      OperationId operationId, TemporalScopeArchetype archetype) {
    /** Validates one command-to-temporal-scope mapping. */
    public CommandTemporalScopeFacts {
      operationId = ContractDescriptorValidation.requireValue(operationId, "operationId");
      archetype = ContractDescriptorValidation.requireValue(archetype, "archetype");
    }
  }
}
