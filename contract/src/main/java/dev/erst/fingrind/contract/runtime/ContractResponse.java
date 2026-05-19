package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractRequestShapes;
import dev.erst.fingrind.contract.discovery.DescriptorNamespaceSupport;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.AccountingBaselineTarget;
import dev.erst.fingrind.contract.protocol.AccountingPolicyPackFacts;
import dev.erst.fingrind.contract.protocol.PlanFailurePolicy;
import dev.erst.fingrind.contract.protocol.PlanTransactionMode;
import dev.erst.fingrind.contract.protocol.PolicySeamFacts;
import dev.erst.fingrind.contract.protocol.ProtocolFailureStatus;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import dev.erst.fingrind.contract.protocol.ProtocolSuccessStatus;
import dev.erst.fingrind.contract.protocol.ReportCapabilityFacts;
import dev.erst.fingrind.core.WireValue;
import java.util.List;

/** Response and model descriptor namespace for the public machine-readable CLI contract. */
public final class ContractResponse {
  private ContractResponse() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return DescriptorNamespaceSupport.descriptorTypes(ResponseDescriptorType.class);
  }

  /** Sealed inventory root for the response descriptor namespace. */
  public sealed interface ResponseDescriptorType
      permits BookModelDescriptor,
          AccountingBaselineDescriptor,
          FieldDescriptor,
          ErrorDescriptor,
          ResponseModelDescriptor,
          PlanExecutionDescriptor,
          RejectionDescriptor,
          AuditDescriptor,
          AccountRegistryDescriptor,
          ReversalDescriptor,
          PreflightDescriptor,
          CurrencyDescriptor,
          ExtensionSurfaceDescriptor {}

  /** Stable initialization requirements for account-registry operations. */
  public enum InitializationRequirement implements WireValue {
    REQUIRES_OPEN_BOOK("requires-open-book");

    private final String wireValue;

    InitializationRequirement(String wireValue) {
      this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
    }

    /** Returns the stable public wire value for this requirement. */
    @Override
    public String wireValue() {
      return wireValue;
    }

    @Override
    public String toString() {
      return wireValue;
    }
  }

  /** Stable relationship between preflight acceptance and the later commit attempt. */
  public enum CommitGuarantee implements WireValue {
    NOT_GUARANTEED("not-guaranteed"),
    GUARANTEED("guaranteed");

    private final String wireValue;

    CommitGuarantee(String wireValue) {
      this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
    }

    /** Returns the stable public wire value for this guarantee status. */
    @Override
    public String wireValue() {
      return wireValue;
    }

    /** Maps one legacy boolean guarantee flag onto the stable enum contract. */
    public static CommitGuarantee fromGuaranteed(boolean guaranteed) {
      return guaranteed ? GUARANTEED : NOT_GUARANTEED;
    }

    @Override
    public String toString() {
      return wireValue;
    }
  }

  /** Descriptor for the machine-readable book model. */
  public record BookModelDescriptor(
      String boundary,
      String entityScope,
      String filesystem,
      String credential,
      String initialization,
      String accountRegistry,
      String currencyScope)
      implements ResponseDescriptorType {
    /** Validates one book-model descriptor payload. */
    public BookModelDescriptor {
      boundary = ContractDescriptorValidation.requireText(boundary, "boundary");
      entityScope = ContractDescriptorValidation.requireText(entityScope, "entityScope");
      filesystem = ContractDescriptorValidation.requireText(filesystem, "filesystem");
      credential = ContractDescriptorValidation.requireText(credential, "credential");
      initialization = ContractDescriptorValidation.requireText(initialization, "initialization");
      accountRegistry =
          ContractDescriptorValidation.requireText(accountRegistry, "accountRegistry");
      currencyScope = ContractDescriptorValidation.requireText(currencyScope, "currencyScope");
    }
  }

  /** Descriptor for the machine-readable accounting baseline. */
  public record AccountingBaselineDescriptor(
      String scope,
      AccountingBaselineTarget currentTarget,
      AccountingBaselineTarget nextTarget,
      List<String> doctrineSources,
      List<String> builtInStatements,
      List<String> deliberateExclusions,
      List<String> nonClaims,
      List<ReportCapabilityFacts> reportCapabilities,
      AccountingPolicyPackFacts defaultPolicyPack,
      String standardsPosition,
      String reportingPosition,
      String chartModelPosition,
      String smallEntityPosition,
      String operationalPosition,
      String taxPosition,
      String organizationalPosition,
      String isoClarification)
      implements ResponseDescriptorType {
    /** Validates one accounting-baseline descriptor payload. */
    public AccountingBaselineDescriptor {
      scope = ContractDescriptorValidation.requireText(scope, "scope");
      currentTarget = ContractDescriptorValidation.requireValue(currentTarget, "currentTarget");
      nextTarget = ContractDescriptorValidation.requireValue(nextTarget, "nextTarget");
      doctrineSources = ContractDescriptorValidation.copyList(doctrineSources, "doctrineSources");
      builtInStatements =
          ContractDescriptorValidation.copyList(builtInStatements, "builtInStatements");
      deliberateExclusions =
          ContractDescriptorValidation.copyList(deliberateExclusions, "deliberateExclusions");
      nonClaims = ContractDescriptorValidation.copyList(nonClaims, "nonClaims");
      reportCapabilities =
          ContractDescriptorValidation.copyList(reportCapabilities, "reportCapabilities");
      defaultPolicyPack =
          ContractDescriptorValidation.requireValue(defaultPolicyPack, "defaultPolicyPack");
      standardsPosition =
          ContractDescriptorValidation.requireText(standardsPosition, "standardsPosition");
      reportingPosition =
          ContractDescriptorValidation.requireText(reportingPosition, "reportingPosition");
      chartModelPosition =
          ContractDescriptorValidation.requireText(chartModelPosition, "chartModelPosition");
      smallEntityPosition =
          ContractDescriptorValidation.requireText(smallEntityPosition, "smallEntityPosition");
      operationalPosition =
          ContractDescriptorValidation.requireText(operationalPosition, "operationalPosition");
      taxPosition = ContractDescriptorValidation.requireText(taxPosition, "taxPosition");
      organizationalPosition =
          ContractDescriptorValidation.requireText(
              organizationalPosition, "organizationalPosition");
      isoClarification =
          ContractDescriptorValidation.requireText(isoClarification, "isoClarification");
    }
  }

  /** One general field descriptor for envelopes or emitted payloads. */
  public record FieldDescriptor(String name, String description) implements ResponseDescriptorType {
    /** Validates one field descriptor payload. */
    public FieldDescriptor {
      name = ContractDescriptorValidation.requireText(name, "name");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** One stable machine error descriptor. */
  public record ErrorDescriptor(String code, String description, List<FieldDescriptor> detailFields)
      implements ResponseDescriptorType {
    /** Creates one error descriptor with no structured detail payload. */
    public ErrorDescriptor(String code, String description) {
      this(code, description, List.of());
    }

    /** Validates the structured error descriptor payload. */
    public ErrorDescriptor {
      code = ContractDescriptorValidation.requireText(code, "code");
      description = ContractDescriptorValidation.requireText(description, "description");
      detailFields = ContractDescriptorValidation.copyList(detailFields, "detailFields");
    }
  }

  /** Descriptor for the stable response contract. */
  public record ResponseModelDescriptor(
      List<ProtocolSuccessStatus> successStatuses,
      List<FieldDescriptor> successFields,
      List<ProtocolRejectionStatus> rejectionStatuses,
      ProtocolFailureStatus errorStatus,
      List<RejectionDescriptor> rejections,
      List<ErrorDescriptor> errorDescriptors,
      List<FieldDescriptor> rejectionFields,
      List<FieldDescriptor> postEntryRejectionFields,
      List<FieldDescriptor> errorFields)
      implements ResponseDescriptorType {
    /** Validates one response-model descriptor payload. */
    public ResponseModelDescriptor {
      successStatuses = ContractDescriptorValidation.copyList(successStatuses, "successStatuses");
      successFields = ContractDescriptorValidation.copyList(successFields, "successFields");
      rejectionStatuses =
          ContractDescriptorValidation.copyList(rejectionStatuses, "rejectionStatuses");
      errorStatus = ContractDescriptorValidation.requireValue(errorStatus, "errorStatus");
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
      PlanTransactionMode transactionMode,
      PlanFailurePolicy failurePolicy,
      String journal,
      List<String> hardLimitations)
      implements ResponseDescriptorType {
    /** Validates one plan-execution descriptor payload. */
    public PlanExecutionDescriptor {
      transactionMode =
          ContractDescriptorValidation.requireValue(transactionMode, "transactionMode");
      failurePolicy = ContractDescriptorValidation.requireValue(failurePolicy, "failurePolicy");
      journal = ContractDescriptorValidation.requireText(journal, "journal");
      hardLimitations = ContractDescriptorValidation.copyList(hardLimitations, "hardLimitations");
    }
  }

  /** One stable machine rejection descriptor. */
  public record RejectionDescriptor(
      String code,
      String description,
      List<FieldDescriptor> detailFields,
      List<RejectionDescriptor> detailRejections)
      implements ResponseDescriptorType {
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
      List<FieldDescriptor> requestProvenanceFields, List<FieldDescriptor> committedFields)
      implements ResponseDescriptorType {
    /** Validates one audit descriptor payload. */
    public AuditDescriptor {
      requestProvenanceFields =
          ContractDescriptorValidation.copyList(requestProvenanceFields, "requestProvenanceFields");
      committedFields = ContractDescriptorValidation.copyList(committedFields, "committedFields");
    }
  }

  /** Descriptor for the book-local account registry contract. */
  public record AccountRegistryDescriptor(
      InitializationRequirement initializationRequirement,
      String redeclarationBehavior,
      List<FieldDescriptor> declareAccountFields,
      List<FieldDescriptor> listFields,
      List<ContractRequestShapes.EnumVocabularyDescriptor> enumVocabularies)
      implements ResponseDescriptorType {
    /** Validates one account-registry descriptor payload. */
    public AccountRegistryDescriptor {
      initializationRequirement =
          ContractDescriptorValidation.requireValue(
              initializationRequirement, "initializationRequirement");
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
  public record ReversalDescriptor(String model, List<String> requirements)
      implements ResponseDescriptorType {
    /** Validates one reversal descriptor payload. */
    public ReversalDescriptor {
      model = ContractDescriptorValidation.requireText(model, "model");
      requirements = ContractDescriptorValidation.copyList(requirements, "requirements");
    }
  }

  /** Descriptor for preflight semantics. */
  public record PreflightDescriptor(
      String semantics, CommitGuarantee commitGuarantee, String description)
      implements ResponseDescriptorType {
    /** Validates one preflight descriptor payload. */
    public PreflightDescriptor {
      semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
      commitGuarantee =
          ContractDescriptorValidation.requireValue(commitGuarantee, "commitGuarantee");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** Descriptor for currency support. */
  public record CurrencyDescriptor(String scope, String multiCurrencyStatus, String description)
      implements ResponseDescriptorType {
    /** Validates one currency descriptor payload. */
    public CurrencyDescriptor {
      scope = ContractDescriptorValidation.requireText(scope, "scope");
      multiCurrencyStatus =
          ContractDescriptorValidation.requireText(multiCurrencyStatus, "multiCurrencyStatus");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }

  /** Descriptor for sanctioned implemented executable policy seams. */
  public record ExtensionSurfaceDescriptor(
      String model,
      String defaultPolicyPackId,
      List<String> implementedSeams,
      List<PolicySeamFacts> policySeams,
      String description)
      implements ResponseDescriptorType {
    /** Validates one extension-surface descriptor payload. */
    public ExtensionSurfaceDescriptor {
      model = ContractDescriptorValidation.requireText(model, "model");
      defaultPolicyPackId =
          ContractDescriptorValidation.requireText(defaultPolicyPackId, "defaultPolicyPackId");
      implementedSeams =
          ContractDescriptorValidation.copyList(implementedSeams, "implementedSeams");
      policySeams = ContractDescriptorValidation.copyList(policySeams, "policySeams");
      description = ContractDescriptorValidation.requireText(description, "description");
    }
  }
}
