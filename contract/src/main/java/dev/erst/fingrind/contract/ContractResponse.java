package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Response and model descriptor namespace for the public machine-readable CLI contract. */
public final class ContractResponse {
  private ContractResponse() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return List.of(
        BookModelDescriptor.class,
        FieldDescriptor.class,
        ErrorDescriptor.class,
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
      String currencyScope) {
    /** Validates one book-model descriptor payload. */
    public BookModelDescriptor {
      boundary = ContractDescriptorValidation.requireText(boundary, "boundary");
      entityScope = ContractDescriptorValidation.requireText(entityScope, "entityScope");
      filesystem = ContractDescriptorValidation.requireText(filesystem, "filesystem");
      credential = ContractDescriptorValidation.requireText(credential, "credential");
      initialization = ContractDescriptorValidation.requireText(initialization, "initialization");
      accountRegistry =
          ContractDescriptorValidation.requireText(accountRegistry, "accountRegistry");
      migration = ContractDescriptorValidation.requireText(migration, "migration");
      currencyScope = ContractDescriptorValidation.requireText(currencyScope, "currencyScope");
    }
  }

  /** One general field descriptor for envelopes or emitted payloads. */
  public record FieldDescriptor(String name, String description) {
    /** Validates one field descriptor payload. */
    public FieldDescriptor {
      name = ContractDescriptorValidation.requireText(name, "name");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** One stable machine error descriptor. */
  public record ErrorDescriptor(String code, String description) {
    /** Validates the structured error descriptor payload. */
    public ErrorDescriptor {
      code = ContractDescriptorValidation.requireText(code, "code");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** Descriptor for the stable response contract. */
  public record ResponseModelDescriptor(
      List<String> successStatuses,
      List<String> rejectionStatuses,
      String errorStatus,
      List<RejectionDescriptor> rejections,
      List<ErrorDescriptor> errorDescriptors,
      List<FieldDescriptor> rejectionFields,
      List<FieldDescriptor> postEntryRejectionFields,
      List<FieldDescriptor> errorFields) {
    /** Validates one response-model descriptor payload. */
    public ResponseModelDescriptor {
      successStatuses = ContractDescriptorValidation.copyList(successStatuses, "successStatuses");
      rejectionStatuses =
          ContractDescriptorValidation.copyList(rejectionStatuses, "rejectionStatuses");
      errorStatus = ContractDescriptorValidation.requireText(errorStatus, "errorStatus");
      rejections = ContractDescriptorValidation.copyList(rejections, "rejections");
      errorDescriptors =
          ContractDescriptorValidation.copyList(errorDescriptors, "errorDescriptors");
      rejectionFields = ContractDescriptorValidation.copyList(rejectionFields, "rejectionFields");
      postEntryRejectionFields =
          ContractDescriptorValidation.copyList(
              postEntryRejectionFields, "postEntryRejectionFields");
      errorFields = ContractDescriptorValidation.copyList(errorFields, "errorFields");
    }
  }

  /** Descriptor for ledger-plan execution semantics. */
  public record PlanExecutionDescriptor(
      String transactionMode, String failurePolicy, String journal, List<String> hardLimitations) {
    /** Validates one plan-execution descriptor payload. */
    public PlanExecutionDescriptor {
      transactionMode =
          ContractDescriptorValidation.requireText(transactionMode, "transactionMode");
      failurePolicy = ContractDescriptorValidation.requireText(failurePolicy, "failurePolicy");
      journal = ContractDescriptorValidation.requireText(journal, "journal");
      hardLimitations = ContractDescriptorValidation.copyList(hardLimitations, "hardLimitations");
    }
  }

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
      code = ContractDescriptorValidation.requireText(code, "code");
      description = ContractDescriptorValidation.requireText(description, "description");
      detailFields = ContractDescriptorValidation.copyList(detailFields, "detailFields");
      detailRejections =
          ContractDescriptorValidation.copyList(detailRejections, "detailRejections");
    }
  }

  /** Descriptor for caller-supplied versus committed audit fields. */
  public record AuditDescriptor(
      List<FieldDescriptor> requestProvenanceFields, List<FieldDescriptor> committedFields) {
    /** Validates one audit descriptor payload. */
    public AuditDescriptor {
      requestProvenanceFields =
          ContractDescriptorValidation.copyList(requestProvenanceFields, "requestProvenanceFields");
      committedFields = ContractDescriptorValidation.copyList(committedFields, "committedFields");
    }
  }

  /** Descriptor for the book-local account registry contract. */
  public record AccountRegistryDescriptor(
      boolean requiresOpenBook,
      String redeclarationBehavior,
      List<FieldDescriptor> declareAccountFields,
      List<FieldDescriptor> listFields,
      List<ContractRequestShapes.EnumVocabularyDescriptor> enumVocabularies) {
    /** Validates one account-registry descriptor payload. */
    public AccountRegistryDescriptor {
      redeclarationBehavior =
          ContractDescriptorValidation.requireText(redeclarationBehavior, "redeclarationBehavior");
      declareAccountFields =
          ContractDescriptorValidation.copyList(declareAccountFields, "declareAccountFields");
      listFields = ContractDescriptorValidation.copyList(listFields, "listFields");
      enumVocabularies =
          ContractDescriptorValidation.copyList(enumVocabularies, "enumVocabularies");
    }
  }

  /** Descriptor for the reversal model. */
  public record ReversalDescriptor(String model, List<String> requirements) {
    /** Validates one reversal descriptor payload. */
    public ReversalDescriptor {
      model = ContractDescriptorValidation.requireText(model, "model");
      requirements = ContractDescriptorValidation.copyList(requirements, "requirements");
    }
  }

  /** Descriptor for preflight semantics. */
  public record PreflightDescriptor(
      String semantics, boolean isCommitGuarantee, String description) {
    /** Validates one preflight descriptor payload. */
    public PreflightDescriptor {
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** Descriptor for currency support. */
  public record CurrencyDescriptor(String scope, String multiCurrencyStatus, String description) {
    /** Validates one currency descriptor payload. */
    public CurrencyDescriptor {
      scope = ContractDescriptorValidation.requireText(scope, "scope");
      multiCurrencyStatus =
          ContractDescriptorValidation.requireText(multiCurrencyStatus, "multiCurrencyStatus");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }
}
