package dev.erst.fingrind.contract;

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
      String stdinToken,
      String bookFileSemantics,
      List<String> bookPassphraseSemantics,
      List<String> requestDocumentSemantics) {}

  /** Descriptor grouping the current request shapes. */
  public record RequestShapesDescriptor(
      PostEntryRequestShapeDescriptor postEntry,
      DeclareAccountRequestShapeDescriptor declareAccount,
      LedgerPlanRequestShapeDescriptor ledgerPlan) {}

  /** Descriptor for the posting request shape shared by single-step and plan execution. */
  public record PostEntryRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<RequestFieldDescriptor> lineFields,
      List<RequestFieldDescriptor> provenanceFields,
      List<RequestFieldDescriptor> reversalFields,
      List<EnumVocabularyDescriptor> enumVocabularies) {}

  /** Descriptor for the declare-account request shape. */
  public record DeclareAccountRequestShapeDescriptor(
      List<RequestFieldDescriptor> topLevelFields,
      List<EnumVocabularyDescriptor> enumVocabularies) {}

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
      ContractResponse.PlanExecutionDescriptor execution) {}

  /** One request field with live presence and description metadata. */
  public record RequestFieldDescriptor(String name, String presence, String description) {}

  /** One live enum vocabulary descriptor. */
  public record EnumVocabularyDescriptor(String name, List<String> values) {}
}
