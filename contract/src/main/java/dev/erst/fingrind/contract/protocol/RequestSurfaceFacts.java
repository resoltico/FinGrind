package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Objects;

/** Canonical request-surface facts shared by validation, discovery, help, and CLI text. */
public record RequestSurfaceFacts(
    List<BookkeepingEntryKindFacts> bookkeepingEntryKinds,
    List<ReachabilityCellFacts> reachabilityMatrix,
    EvidenceRequirementFacts bookkeepingEntryEvidence,
    List<TemporalScopeFacts> temporalScopes,
    List<CommandTemporalScopeFacts> commandTemporalScopes) {
  /** Validates one request-surface fact bundle. */
  public RequestSurfaceFacts {
    bookkeepingEntryKinds =
        ContractDescriptorValidation.copyList(bookkeepingEntryKinds, "bookkeepingEntryKinds");
    reachabilityMatrix =
        ContractDescriptorValidation.copyList(reachabilityMatrix, "reachabilityMatrix");
    bookkeepingEntryEvidence =
        ContractDescriptorValidation.requireValue(
            bookkeepingEntryEvidence, "bookkeepingEntryEvidence");
    temporalScopes = ContractDescriptorValidation.copyList(temporalScopes, "temporalScopes");
    commandTemporalScopes =
        ContractDescriptorValidation.copyList(commandTemporalScopes, "commandTemporalScopes");
    RequestSurfaceFactsValidation.requireUniqueEntryKinds(bookkeepingEntryKinds);
    RequestSurfaceFactsValidation.requireUniqueReachabilityCells(reachabilityMatrix);
    RequestSurfaceFactsValidation.requireUniqueTemporalArchetypes(temporalScopes);
    RequestSurfaceFactsValidation.requireUniqueTemporalScopeCommands(commandTemporalScopes);
  }

  /** Returns the canonical entry-kind facts for one posting request kind. */
  public BookkeepingEntryKindFacts bookkeepingEntryKind(BookkeepingEntryKind entryKind) {
    BookkeepingEntryKind requiredEntryKind = Objects.requireNonNull(entryKind, "entryKind");
    return bookkeepingEntryKinds.stream()
        .filter(facts -> facts.entryKind() == requiredEntryKind)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No bookkeeping-entry request facts are registered for "
                        + requiredEntryKind
                        + "."));
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
  public record BookkeepingEntryKindFacts(
      BookkeepingEntryKind entryKind,
      List<String> requiredTopLevelFields,
      List<String> optionalTopLevelFields,
      List<String> forbiddenTopLevelFields,
      List<String> requiredSourceDocumentFields,
      SourceDocumentTypeFacts sourceDocumentTypes,
      String semantics) {
    /** Validates one entry-kind request fact descriptor. */
    public BookkeepingEntryKindFacts {
      entryKind = ContractDescriptorValidation.requireValue(entryKind, "entryKind");
      requiredTopLevelFields =
          ContractDescriptorValidation.copyList(requiredTopLevelFields, "requiredTopLevelFields");
      optionalTopLevelFields =
          ContractDescriptorValidation.copyList(optionalTopLevelFields, "optionalTopLevelFields");
      forbiddenTopLevelFields =
          ContractDescriptorValidation.copyList(forbiddenTopLevelFields, "forbiddenTopLevelFields");
      requiredSourceDocumentFields =
          ContractDescriptorValidation.copyList(
              requiredSourceDocumentFields, "requiredSourceDocumentFields");
      if (requiredSourceDocumentFields.isEmpty()) {
        throw new IllegalArgumentException("requiredSourceDocumentFields must not be empty.");
      }
      sourceDocumentTypes =
          ContractDescriptorValidation.requireValue(sourceDocumentTypes, "sourceDocumentTypes");
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
      if (!java.util.Collections.disjoint(requiredTopLevelFields, optionalTopLevelFields)
          || !java.util.Collections.disjoint(requiredTopLevelFields, forbiddenTopLevelFields)
          || !java.util.Collections.disjoint(optionalTopLevelFields, forbiddenTopLevelFields)) {
        throw new IllegalArgumentException(
            "requiredTopLevelFields, optionalTopLevelFields, and forbiddenTopLevelFields must be disjoint.");
      }
    }
  }

  /** Canonical source-document type facts for one posting entry kind. */
  public record SourceDocumentTypeFacts(
      SourceDocumentTypePolicyMode mode,
      List<String> acceptedValues,
      String semantics,
      String scaffoldValue) {
    /** Validates one source-document type fact bundle. */
    public SourceDocumentTypeFacts {
      mode = ContractDescriptorValidation.requireValue(mode, "mode");
      acceptedValues = ContractDescriptorValidation.copyList(acceptedValues, "acceptedValues");
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
      scaffoldValue = ContractDescriptorValidation.requireText(scaffoldValue, "scaffoldValue");
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
  public record EvidenceRequirementFacts(String description, int minimumSourceDocuments) {
    /** Validates one evidence-requirement fact bundle. */
    public EvidenceRequirementFacts {
      description = ContractDescriptorValidation.requireText(description, "description");
      if (minimumSourceDocuments < 1) {
        throw new IllegalArgumentException("minimumSourceDocuments must be at least one.");
      }
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
