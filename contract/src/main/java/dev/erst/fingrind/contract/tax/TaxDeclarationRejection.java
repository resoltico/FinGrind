package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic refusals for tax-registration declaration. */
public sealed interface TaxDeclarationRejection
    permits TaxDeclarationRejection.BookNotInitialized,
        TaxDeclarationRejection.DefinitionViolations {

  /** Returns the stable wire code for one tax-declaration rejection instance. */
  static String wireCode(TaxDeclarationRejection rejection) {
    return descriptorFor(rejection).code();
  }

  /** Returns the canonical machine descriptors for every tax-declaration rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Rejection for commands that require an initialized book but found none. */
  record BookNotInitialized() implements TaxDeclarationRejection {}

  /** Rejection for one invalid tax-registration definition. */
  record DefinitionViolations(List<TaxDefinitionViolation> violations)
      implements TaxDeclarationRejection {
    /** Validates the tax-definition violation payload. */
    public DefinitionViolations {
      violations = ContractDescriptorValidation.copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException(
            "Definition violations must contain at least one issue.");
      }
    }
  }

  private static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  private static Descriptor descriptorFor(TaxDeclarationRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case TaxDeclarationRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case TaxDeclarationRejection.DefinitionViolations _ -> Descriptor.DEFINITION_VIOLATIONS;
    };
  }

  /** Canonical tax-declaration rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED,
    DEFINITION_VIOLATIONS;

    private String code() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> "tax-book-not-initialized";
        case DEFINITION_VIOLATIONS -> "tax-definition-violations";
      };
    }

    private String description() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED ->
            "Tax registration declaration refused because the selected book does not exist or has not been initialized with "
                + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
                + ".";
        case DEFINITION_VIOLATIONS ->
            "Tax registration declaration refused because one or more requested tax-definition fields violate the owned tax context.";
      };
    }

    private List<ContractResponse.FieldDescriptor> detailFields() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> List.of();
        case DEFINITION_VIOLATIONS ->
            List.of(
                detailField(
                    "violations",
                    "Structured tax-definition violations describing which requested fields are invalid and why."));
      };
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return new ContractResponse.RejectionDescriptor(
          code(), category(), description(), detailFields(), List.of());
    }

    private ContractResponse.FailureCategory category() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> ContractResponse.FailureCategory.PRECONDITION;
        case DEFINITION_VIOLATIONS -> ContractResponse.FailureCategory.DOMAIN_SEMANTIC;
      };
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return List.of(values()).stream().map(Descriptor::descriptor).toList();
    }
  }
}
