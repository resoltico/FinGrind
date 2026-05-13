package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.LedgerAssertionKind;
import dev.erst.fingrind.contract.protocol.LedgerStepKind;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Map;

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
          PostEntryRequestShapeDescriptor,
          DeclareAccountRequestShapeDescriptor,
          LedgerPlanRequestShapeDescriptor,
          RequestFieldDescriptor,
          EnumVocabularyDescriptor {}

  /** Descriptor for request-file and book-file input plumbing. */
  public record RequestInputDescriptor(
      String bookFileOption,
      List<String> bookPassphraseOptions,
      String requestFileOption,
      String outputOption,
      List<String> outputSemantics,
      String stdinToken,
      String bookFileSemantics,
      List<String> bookPassphraseSemantics,
      List<String> requestDocumentSemantics)
      implements RequestShapeDescriptorType {
    /** Validates one request-input descriptor payload. */
    public RequestInputDescriptor {
      bookFileOption = ContractDescriptorValidation.requireText(bookFileOption, "bookFileOption");
      bookPassphraseOptions =
          ContractDescriptorValidation.copyList(bookPassphraseOptions, "bookPassphraseOptions");
      requestFileOption =
          ContractDescriptorValidation.requireText(requestFileOption, "requestFileOption");
      outputOption = ContractDescriptorValidation.requireText(outputOption, "outputOption");
      outputSemantics = ContractDescriptorValidation.copyList(outputSemantics, "outputSemantics");
      stdinToken = ContractDescriptorValidation.requireText(stdinToken, "stdinToken");
      bookFileSemantics =
          ContractDescriptorValidation.requireText(bookFileSemantics, "bookFileSemantics");
      bookPassphraseSemantics =
          ContractDescriptorValidation.copyList(bookPassphraseSemantics, "bookPassphraseSemantics");
      requestDocumentSemantics =
          ContractDescriptorValidation.copyList(
              requestDocumentSemantics, "requestDocumentSemantics");
    }
  }

  /** Descriptor grouping the current request shapes. */
  public record RequestShapesDescriptor(
      String schemaDialect,
      PostEntryRequestShapeDescriptor postEntry,
      DeclareAccountRequestShapeDescriptor declareAccount,
      LedgerPlanRequestShapeDescriptor ledgerPlan)
      implements RequestShapeDescriptorType {
    /** Validates one grouped request-shape descriptor payload. */
    public RequestShapesDescriptor {
      schemaDialect = ContractDescriptorValidation.requireText(schemaDialect, "schemaDialect");
      postEntry = ContractDescriptorValidation.requireValue(postEntry, "postEntry");
      declareAccount = ContractDescriptorValidation.requireValue(declareAccount, "declareAccount");
      ledgerPlan = ContractDescriptorValidation.requireValue(ledgerPlan, "ledgerPlan");
    }
  }

  /** Descriptor for the posting request shape shared by single-step and plan execution. */
  public record PostEntryRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> lineFields,
      List<RequestFieldDescriptor> provenanceFields,
      List<RequestFieldDescriptor> reversalFields,
      List<EnumVocabularyDescriptor> enumVocabularies,
      Map<String, Object> schema)
      implements RequestShapeDescriptorType {
    /** Validates one post-entry request-shape descriptor payload. */
    public PostEntryRequestShapeDescriptor {
      topLevelFields = ContractDescriptorValidation.copyList(topLevelFields, "topLevelFields");
      lineFields = ContractDescriptorValidation.copyList(lineFields, "lineFields");
      provenanceFields =
          ContractDescriptorValidation.copyList(provenanceFields, "provenanceFields");
      reversalFields = ContractDescriptorValidation.copyList(reversalFields, "reversalFields");
      enumVocabularies =
          ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
      schema = ContractDescriptorValidation.copyMap(schema, "schema");
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
      schema = ContractDescriptorValidation.copyMap(schema, "schema");
    }
  }

  /** Descriptor for the ledger-plan request shape. */
  public record LedgerPlanRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> stepFields,
      List<RequestFieldDescriptor> queryFields,
      List<RequestFieldDescriptor> assertionFields,
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
      administrationStepKinds =
          ContractDescriptorValidation.copyList(administrationStepKinds, "administrationStepKinds");
      queryStepKinds = ContractDescriptorValidation.copyList(queryStepKinds, "queryStepKinds");
      writeStepKinds = ContractDescriptorValidation.copyList(writeStepKinds, "writeStepKinds");
      assertStepKind = ContractDescriptorValidation.requireValue(assertStepKind, "assertStepKind");
      assertionKinds = ContractDescriptorValidation.copyList(assertionKinds, "assertionKinds");
      execution = ContractDescriptorValidation.requireValue(execution, "execution");
      schema = ContractDescriptorValidation.copyMap(schema, "schema");
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
