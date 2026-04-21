package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Request-shape descriptor namespace for the public machine-readable CLI contract. */
public final class ContractRequestShapes {
  private ContractRequestShapes() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return List.of(
        RequestInputDescriptor.class,
        RequestShapesDescriptor.class,
        PostEntryRequestShapeDescriptor.class,
        DeclareAccountRequestShapeDescriptor.class,
        LedgerPlanRequestShapeDescriptor.class,
        RequestFieldDescriptor.class,
        EnumVocabularyDescriptor.class);
  }

  /** Descriptor for request-file and book-file input plumbing. */
  public record RequestInputDescriptor(
      String bookFileOption,
      List<String> bookPassphraseOptions,
      String requestFileOption,
      String queryOutputOption,
      List<String> queryOutputModes,
      List<String> queryOutputSemantics,
      String stdinToken,
      String bookFileSemantics,
      List<String> bookPassphraseSemantics,
      List<String> requestDocumentSemantics) {
    /** Validates one request-input descriptor payload. */
    public RequestInputDescriptor {
      bookFileOption = ContractDescriptorValidation.requireText(bookFileOption, "bookFileOption");
      bookPassphraseOptions =
          ContractDescriptorValidation.copyList(bookPassphraseOptions, "bookPassphraseOptions");
      requestFileOption =
          ContractDescriptorValidation.requireText(requestFileOption, "requestFileOption");
      queryOutputOption =
          ContractDescriptorValidation.requireText(queryOutputOption, "queryOutputOption");
      queryOutputModes =
          ContractDescriptorValidation.copyList(queryOutputModes, "queryOutputModes");
      queryOutputSemantics =
          ContractDescriptorValidation.copyList(queryOutputSemantics, "queryOutputSemantics");
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
      PostEntryRequestShapeDescriptor postEntry,
      DeclareAccountRequestShapeDescriptor declareAccount,
      LedgerPlanRequestShapeDescriptor ledgerPlan) {
    /** Validates one grouped request-shape descriptor payload. */
    public RequestShapesDescriptor {
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
      List<EnumVocabularyDescriptor> enumVocabularies) {
    /** Validates one post-entry request-shape descriptor payload. */
    public PostEntryRequestShapeDescriptor {
      topLevelFields = ContractDescriptorValidation.copyList(topLevelFields, "topLevelFields");
      lineFields = ContractDescriptorValidation.copyList(lineFields, "lineFields");
      provenanceFields =
          ContractDescriptorValidation.copyList(provenanceFields, "provenanceFields");
      reversalFields = ContractDescriptorValidation.copyList(reversalFields, "reversalFields");
      enumVocabularies =
          ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
    }
  }

  /** Descriptor for the declare-account request shape. */
  public record DeclareAccountRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<EnumVocabularyDescriptor> enumVocabularies) {
    /** Validates one declare-account request-shape descriptor payload. */
    public DeclareAccountRequestShapeDescriptor {
      topLevelFields = ContractDescriptorValidation.copyList(topLevelFields, "topLevelFields");
      enumVocabularies =
          ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
    }
  }

  /** Descriptor for the ledger-plan request shape. */
  public record LedgerPlanRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> stepFields,
      List<RequestFieldDescriptor> queryFields,
      List<RequestFieldDescriptor> assertionFields,
      List<String> administrationStepKinds,
      List<String> queryStepKinds,
      List<String> writeStepKinds,
      String assertStepKind,
      List<String> assertionKinds,
      ContractResponse.PlanExecutionDescriptor execution) {
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
      assertStepKind = ContractDescriptorValidation.requireText(assertStepKind, "assertStepKind");
      assertionKinds = ContractDescriptorValidation.copyList(assertionKinds, "assertionKinds");
      execution = ContractDescriptorValidation.requireValue(execution, "execution");
    }
  }

  /** One request field with live presence and description metadata. */
  public record RequestFieldDescriptor(String name, String presence, String description) {
    /** Validates one request-field descriptor payload. */
    public RequestFieldDescriptor {
      name = ContractDescriptorValidation.requireText(name, "name");
      presence = ContractDescriptorValidation.requireText(presence, "presence");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** One live enum vocabulary descriptor. */
  public record EnumVocabularyDescriptor(String name, List<String> values) {
    /** Validates one enum-vocabulary descriptor payload. */
    public EnumVocabularyDescriptor {
      name = ContractDescriptorValidation.requireText(name, "name");
      values = ContractDescriptorValidation.copyList(values, "values");
    }
  }
}
