package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;

/** Response and model descriptor namespace for the public machine-readable CLI contract. */
public final class ContractResponse {
  private ContractResponse() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return List.of(
        BookModelDescriptor.class,
        FieldDescriptor.class,
        ResponseModelDescriptor.class,
        PlanExecutionDescriptor.class,
        RejectionDescriptor.class,
        AuditDescriptor.class,
        AccountRegistryDescriptor.class,
        ReversalDescriptor.class,
        PreflightDescriptor.class,
        CurrencyDescriptor.class);
  }

  /** Descriptor for the machine-readable book model. */
  public record BookModelDescriptor(
      String boundary,
      String entityScope,
      String filesystem,
      String credential,
      String initialization,
      String accountRegistry,
      String migration,
      String currencyScope) {}

  /** One general field descriptor for envelopes or emitted payloads. */
  public record FieldDescriptor(String name, String description) {}

  /** Descriptor for the stable response contract. */
  public record ResponseModelDescriptor(
      List<String> successStatuses,
      List<String> rejectionStatuses,
      String errorStatus,
      List<RejectionDescriptor> rejections,
      List<FieldDescriptor> rejectionFields,
      List<FieldDescriptor> postEntryRejectionFields,
      List<FieldDescriptor> errorFields) {}

  /** Descriptor for ledger-plan execution semantics. */
  public record PlanExecutionDescriptor(
      String transactionMode, String failurePolicy, String journal, List<String> hardLimitations) {}

  /** One stable machine rejection descriptor. */
  public record RejectionDescriptor(
      String code,
      String description,
      List<FieldDescriptor> detailFields,
      List<RejectionDescriptor> detailRejections) {
    /** Creates one rejection descriptor with no structured detail payload. */
    public RejectionDescriptor(String code, String description) {
      this(code, description, List.of(), List.of());
    }

    /** Validates the structured rejection descriptor payload. */
    public RejectionDescriptor {
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(description, "description");
      detailFields = List.copyOf(Objects.requireNonNull(detailFields, "detailFields"));
      detailRejections = List.copyOf(Objects.requireNonNull(detailRejections, "detailRejections"));
    }
  }

  /** Descriptor for caller-supplied versus committed audit fields. */
  public record AuditDescriptor(
      List<FieldDescriptor> requestProvenanceFields, List<FieldDescriptor> committedFields) {}

  /** Descriptor for the book-local account registry contract. */
  public record AccountRegistryDescriptor(
      boolean requiresOpenBook,
      String redeclarationBehavior,
      List<FieldDescriptor> declareAccountFields,
      List<FieldDescriptor> listFields,
      List<ContractRequestShapes.EnumVocabularyDescriptor> enumVocabularies) {}

  /** Descriptor for the reversal model. */
  public record ReversalDescriptor(String model, List<String> requirements) {}

  /** Descriptor for preflight semantics. */
  public record PreflightDescriptor(
      String semantics, boolean isCommitGuarantee, String description) {}

  /** Descriptor for currency support. */
  public record CurrencyDescriptor(String scope, String multiCurrencyStatus, String description) {}
}
