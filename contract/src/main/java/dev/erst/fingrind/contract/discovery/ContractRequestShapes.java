package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.protocol.SourceDocumentTypePolicyMode;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Request-shape descriptor namespace for the public machine-readable CLI contract. */
public final class ContractRequestShapes {
  private ContractRequestShapes() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return DescriptorNamespaceSupport.descriptorTypes(RequestShapeDescriptorType.class);
  }

  /** Sealed inventory root for the request-shape descriptor namespace. */
  public sealed interface RequestShapeDescriptorType
      permits RequestInputDescriptor,
          RequestShapesDescriptor,
          BookkeepingEntryRequestShapeDescriptor,
          DeclareAccountRequestShapeDescriptor,
          DeclareTaxRegistrationRequestShapeDescriptor,
          LedgerPlanRequestShapeDescriptor,
          EntryKindSemanticsDescriptor,
          ReachabilityCellDescriptor,
          EvidenceRequirementDescriptor,
          RequestFieldDescriptor,
          EnumVocabularyDescriptor {}

  /** Descriptor for request-file and book-file input plumbing. */
  public record RequestInputDescriptor(
      String bookFileOption,
      List<String> bookPassphraseOptions,
      String requestFileOption,
      List<String> requestFileCommands,
      List<String> directArgumentCommands,
      String outputOption,
      List<String> outputSemantics,
      String stdinToken,
      String bookFileSemantics,
      int bookPassphraseMaxUtf8Bytes,
      List<String> bookPassphraseSemantics,
      int requestDocumentMaxUtf8Bytes,
      List<String> requestDocumentSemantics)
      implements RequestShapeDescriptorType {
    /** Validates one request-input descriptor payload. */
    public RequestInputDescriptor {
      bookFileOption = ContractDescriptorValidation.requireText(bookFileOption, "bookFileOption");
      bookPassphraseOptions =
          ContractDescriptorValidation.copyList(bookPassphraseOptions, "bookPassphraseOptions");
      requestFileOption =
          ContractDescriptorValidation.requireText(requestFileOption, "requestFileOption");
      requestFileCommands =
          ContractDescriptorValidation.copyList(requestFileCommands, "requestFileCommands");
      directArgumentCommands =
          ContractDescriptorValidation.copyList(directArgumentCommands, "directArgumentCommands");
      outputOption = ContractDescriptorValidation.requireText(outputOption, "outputOption");
      outputSemantics = ContractDescriptorValidation.copyList(outputSemantics, "outputSemantics");
      stdinToken = ContractDescriptorValidation.requireText(stdinToken, "stdinToken");
      bookFileSemantics =
          ContractDescriptorValidation.requireText(bookFileSemantics, "bookFileSemantics");
      if (bookPassphraseMaxUtf8Bytes < 1) {
        throw new IllegalArgumentException("bookPassphraseMaxUtf8Bytes must be positive.");
      }
      bookPassphraseSemantics =
          ContractDescriptorValidation.copyList(bookPassphraseSemantics, "bookPassphraseSemantics");
      if (requestDocumentMaxUtf8Bytes < 1) {
        throw new IllegalArgumentException("requestDocumentMaxUtf8Bytes must be positive.");
      }
      requestDocumentSemantics =
          ContractDescriptorValidation.copyList(
              requestDocumentSemantics, "requestDocumentSemantics");
    }
  }

  /** Descriptor grouping the current request shapes. */
  public record RequestShapesDescriptor(
      String schemaDialect,
      @Nullable BookkeepingEntryRequestShapeDescriptor bookkeepingEntry,
      @Nullable DeclareAccountRequestShapeDescriptor declareAccount,
      @Nullable DeclareTaxRegistrationRequestShapeDescriptor declareTaxRegistration,
      @Nullable LedgerPlanRequestShapeDescriptor ledgerPlan)
      implements RequestShapeDescriptorType {
    /** Validates one grouped request-shape descriptor payload. */
    public RequestShapesDescriptor {
      schemaDialect = ContractDescriptorValidation.requireText(schemaDialect, "schemaDialect");
    }
  }

  /**
   * Descriptor for the bookkeeping-entry request shape shared by single-step and plan execution.
   */
  public record BookkeepingEntryRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> lineFields,
      List<RequestFieldDescriptor> openingBalanceFields,
      List<RequestFieldDescriptor> recognitionIntervalFields,
      List<RequestFieldDescriptor> foreignExchangeFields,
      List<RequestFieldDescriptor> quotedRateFields,
      List<RequestFieldDescriptor> taxFields,
      List<RequestFieldDescriptor> evidenceFields,
      List<RequestFieldDescriptor> sourceDocumentFields,
      List<RequestFieldDescriptor> approvalFields,
      List<RequestFieldDescriptor> provenanceFields,
      List<RequestFieldDescriptor> reversalFields,
      List<EntryKindSemanticsDescriptor> entryKindSemantics,
      List<ReachabilityCellDescriptor> reachabilityMatrix,
      EvidenceRequirementDescriptor evidenceRequirement,
      List<EnumVocabularyDescriptor> enumVocabularies,
      Map<String, Object> schema)
      implements RequestShapeDescriptorType {
    /** Validates one bookkeeping-entry request-shape descriptor payload. */
    public BookkeepingEntryRequestShapeDescriptor {
      topLevelFields = ContractDescriptorValidation.copyList(topLevelFields, "topLevelFields");
      lineFields = ContractDescriptorValidation.copyList(lineFields, "lineFields");
      openingBalanceFields =
          ContractDescriptorValidation.copyList(openingBalanceFields, "openingBalanceFields");
      recognitionIntervalFields =
          ContractDescriptorValidation.copyList(
              recognitionIntervalFields, "recognitionIntervalFields");
      foreignExchangeFields =
          ContractDescriptorValidation.copyList(foreignExchangeFields, "foreignExchangeFields");
      quotedRateFields =
          ContractDescriptorValidation.copyList(quotedRateFields, "quotedRateFields");
      taxFields = ContractDescriptorValidation.copyList(taxFields, "taxFields");
      evidenceFields = ContractDescriptorValidation.copyList(evidenceFields, "evidenceFields");
      sourceDocumentFields =
          ContractDescriptorValidation.copyList(sourceDocumentFields, "sourceDocumentFields");
      approvalFields = ContractDescriptorValidation.copyList(approvalFields, "approvalFields");
      provenanceFields =
          ContractDescriptorValidation.copyList(provenanceFields, "provenanceFields");
      reversalFields = ContractDescriptorValidation.copyList(reversalFields, "reversalFields");
      entryKindSemantics =
          ContractDescriptorValidation.copyList(entryKindSemantics, "entryKindSemantics");
      reachabilityMatrix =
          ContractDescriptorValidation.copyList(reachabilityMatrix, "reachabilityMatrix");
      evidenceRequirement =
          ContractDescriptorValidation.requireValue(evidenceRequirement, "evidenceRequirement");
      enumVocabularies =
          ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
      schema = ContractDescriptorValidation.copySchemaMap(schema, "schema");
    }
  }

  /** Descriptor for the declare-account request shape. */
  public record DeclareAccountRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<EnumVocabularyDescriptor> enumVocabularies,
      Map<String, Object> schema)
      implements RequestShapeDescriptorType {
    /** Validates one declare-account request-shape descriptor payload. */
    public DeclareAccountRequestShapeDescriptor {
      topLevelFields = ContractDescriptorValidation.copyList(topLevelFields, "topLevelFields");
      enumVocabularies =
          ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
      schema = ContractDescriptorValidation.copySchemaMap(schema, "schema");
    }
  }

  /** Descriptor for the declare-tax-registration request shape. */
  public record DeclareTaxRegistrationRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> taxCodeFields,
      List<EnumVocabularyDescriptor> enumVocabularies,
      Map<String, Object> schema)
      implements RequestShapeDescriptorType {
    /** Validates one declare-tax-registration request-shape descriptor payload. */
    public DeclareTaxRegistrationRequestShapeDescriptor {
      topLevelFields = ContractDescriptorValidation.copyList(topLevelFields, "topLevelFields");
      taxCodeFields = ContractDescriptorValidation.copyList(taxCodeFields, "taxCodeFields");
      enumVocabularies =
          ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
      schema = ContractDescriptorValidation.copySchemaMap(schema, "schema");
    }
  }

  /** Descriptor for the ledger-plan request shape. */
  public record LedgerPlanRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> stepFields,
      List<RequestFieldDescriptor> queryFields,
      List<RequestFieldDescriptor> assertionFields,
      BookkeepingEntryRequestShapeDescriptor postingModel,
      List<LedgerStepKind> administrationStepKinds,
      List<LedgerStepKind> queryStepKinds,
      List<LedgerStepKind> writeStepKinds,
      LedgerStepKind assertStepKind,
      List<LedgerAssertionKind> assertionKinds,
      ContractResponse.PlanExecutionDescriptor execution,
      Map<String, Object> schema)
      implements RequestShapeDescriptorType {
    /** Validates one ledger-plan request-shape descriptor payload. */
    public LedgerPlanRequestShapeDescriptor {
      topLevelFields = ContractDescriptorValidation.copyList(topLevelFields, "topLevelFields");
      stepFields = ContractDescriptorValidation.copyList(stepFields, "stepFields");
      queryFields = ContractDescriptorValidation.copyList(queryFields, "queryFields");
      assertionFields = ContractDescriptorValidation.copyList(assertionFields, "assertionFields");
      postingModel = ContractDescriptorValidation.requireValue(postingModel, "postingModel");
      administrationStepKinds =
          ContractDescriptorValidation.copyList(administrationStepKinds, "administrationStepKinds");
      queryStepKinds = ContractDescriptorValidation.copyList(queryStepKinds, "queryStepKinds");
      writeStepKinds = ContractDescriptorValidation.copyList(writeStepKinds, "writeStepKinds");
      assertStepKind = ContractDescriptorValidation.requireValue(assertStepKind, "assertStepKind");
      assertionKinds = ContractDescriptorValidation.copyList(assertionKinds, "assertionKinds");
      execution = ContractDescriptorValidation.requireValue(execution, "execution");
      schema = ContractDescriptorValidation.copySchemaMap(schema, "schema");
    }
  }

  /** Descriptor for one bookkeeping-entry kind's request semantics. */
  public record EntryKindSemanticsDescriptor(
      BookkeepingEntryKind entryKind,
      List<String> requiredTopLevelFields,
      List<String> optionalTopLevelFields,
      List<String> forbiddenTopLevelFields,
      List<String> requiredSourceDocumentFields,
      String sourceDocumentTypeMode,
      List<String> acceptedSourceDocumentTypes,
      String sourceDocumentTypeSemantics,
      String semantics)
      implements RequestShapeDescriptorType {
    /** Validates one posting-entry-kind semantics descriptor payload. */
    public EntryKindSemanticsDescriptor {
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
      sourceDocumentTypeMode =
          ContractDescriptorValidation.requireText(
              sourceDocumentTypeMode, "sourceDocumentTypeMode");
      SourceDocumentTypePolicyMode.fromWireValue(sourceDocumentTypeMode);
      acceptedSourceDocumentTypes =
          ContractDescriptorValidation.copyList(
              acceptedSourceDocumentTypes, "acceptedSourceDocumentTypes");
      sourceDocumentTypeSemantics =
          ContractDescriptorValidation.requireText(
              sourceDocumentTypeSemantics, "sourceDocumentTypeSemantics");
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
    }
  }

  /** Descriptor for one published reachability-matrix cell. */
  public record ReachabilityCellDescriptor(
      String classificationFamily,
      AccountType accountType,
      String classification,
      boolean declarable,
      boolean openingReachable,
      boolean operationalJournalReachable,
      boolean reversalReachable)
      implements RequestShapeDescriptorType {
    /** Validates one reachability-matrix descriptor payload. */
    public ReachabilityCellDescriptor {
      classificationFamily =
          ContractDescriptorValidation.requireText(classificationFamily, "classificationFamily");
      accountType = ContractDescriptorValidation.requireValue(accountType, "accountType");
      classification = ContractDescriptorValidation.requireText(classification, "classification");
    }
  }

  /** Descriptor for the non-negotiable source-document evidence contract. */
  public record EvidenceRequirementDescriptor(String description, int minimumSourceDocuments)
      implements RequestShapeDescriptorType {
    /** Validates one evidence-requirement descriptor payload. */
    public EvidenceRequirementDescriptor {
      description = ContractDescriptorValidation.requireText(description, "description");
      if (minimumSourceDocuments < 1) {
        throw new IllegalArgumentException("minimumSourceDocuments must be at least one.");
      }
    }
  }

  /** One request field with live presence and description metadata. */
  public record RequestFieldDescriptor(
      String name, RequestFieldPresence presence, String description)
      implements RequestShapeDescriptorType {
    /** Validates one request-field descriptor payload. */
    public RequestFieldDescriptor {
      name = ContractDescriptorValidation.requireText(name, "name");
      presence = ContractDescriptorValidation.requireValue(presence, "presence");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** One live enum vocabulary descriptor. */
  public record EnumVocabularyDescriptor(String name, List<String> values)
      implements RequestShapeDescriptorType {
    /** Validates one enum-vocabulary descriptor payload. */
    public EnumVocabularyDescriptor {
      name = ContractDescriptorValidation.requireText(name, "name");
      values = ContractDescriptorValidation.copyList(values, "values");
    }
  }
}
