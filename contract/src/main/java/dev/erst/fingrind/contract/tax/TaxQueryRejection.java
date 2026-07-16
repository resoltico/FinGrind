package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.internal.ContractRejectionDescriptors;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Closed family of deterministic rejections for tax query and report commands. */
public sealed interface TaxQueryRejection
    permits TaxQueryRejection.BookNotInitialized,
        TaxQueryRejection.UnknownTaxRegistration,
        TaxQueryRejection.ObligationPeriodMismatch {

  /** Returns the stable wire code for one tax query rejection instance. */
  static String wireCode(TaxQueryRejection rejection) {
    return descriptorFor(rejection).code();
  }

  /** Returns the canonical machine descriptors for every tax query rejection. */
  static List<ContractResponse.RejectionDescriptor> descriptors() {
    return Descriptor.descriptors();
  }

  /** Rejection for a query against a missing or uninitialized book. */
  record BookNotInitialized() implements TaxQueryRejection {}

  /** Rejection for a query that names a tax registration not declared in this book. */
  record UnknownTaxRegistration(TaxRegistrationId taxRegistrationId) implements TaxQueryRejection {
    public UnknownTaxRegistration {
      Objects.requireNonNull(taxRegistrationId, "taxRegistrationId");
    }
  }

  /** Rejection for an obligation query whose requested period does not match the filing cadence. */
  record ObligationPeriodMismatch(
      TaxObligationFrequency obligationFrequency,
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo)
      implements TaxQueryRejection {
    public ObligationPeriodMismatch {
      Objects.requireNonNull(obligationFrequency, "obligationFrequency");
      Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
      Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    }
  }

  private static Descriptor descriptorFor(TaxQueryRejection rejection) {
    return switch (Objects.requireNonNull(rejection, "rejection")) {
      case TaxQueryRejection.BookNotInitialized _ -> Descriptor.BOOK_NOT_INITIALIZED;
      case TaxQueryRejection.UnknownTaxRegistration _ -> Descriptor.UNKNOWN_TAX_REGISTRATION;
      case TaxQueryRejection.ObligationPeriodMismatch _ -> Descriptor.OBLIGATION_PERIOD_MISMATCH;
    };
  }

  /** Canonical tax-query rejection metadata keyed by stable wire code. */
  enum Descriptor {
    BOOK_NOT_INITIALIZED(
        "tax-query-book-not-initialized",
        "Tax query refused because the selected book does not exist or has not been initialized with "
            + ProtocolCatalog.operationName(OperationId.OPEN_BOOK)
            + ".") {
      @Override
      List<ContractResponse.FieldDescriptor> detailFields() {
        return List.of();
      }
    },
    UNKNOWN_TAX_REGISTRATION(
        "unknown-tax-registration",
        "Tax query refused because the selected taxRegistrationId is not declared in this book.") {
      @Override
      List<ContractResponse.FieldDescriptor> detailFields() {
        return List.of(
            ContractRejectionDescriptors.detailField(
                "taxRegistrationId",
                "Tax registration identifier supplied by the caller that is not declared in this book."));
      }
    },
    OBLIGATION_PERIOD_MISMATCH(
        "tax-obligation-period-mismatch",
        "Tax obligation query refused because the requested period does not match the declared filing cadence for the selected tax registration.") {
      @Override
      List<ContractResponse.FieldDescriptor> detailFields() {
        return List.of(
            ContractRejectionDescriptors.detailField(
                "obligationFrequency",
                "Declared filing cadence that the requested period must satisfy."),
            ContractRejectionDescriptors.detailField(
                "effectiveDateFrom",
                "Requested inclusive period start that failed cadence validation."),
            ContractRejectionDescriptors.detailField(
                "effectiveDateTo",
                "Requested inclusive period end that failed cadence validation."));
      }
    };

    private final String code;
    private final String description;

    Descriptor(String code, String description) {
      this.code = code;
      this.description = description;
    }

    private String code() {
      return code;
    }

    private static List<ContractResponse.RejectionDescriptor> descriptors() {
      return ContractRejectionDescriptors.descriptors(values(), Descriptor::descriptor);
    }

    private ContractResponse.RejectionDescriptor descriptor() {
      return ContractRejectionDescriptors.descriptor(code, category(), description, detailFields());
    }

    private ContractResponse.FailureCategory category() {
      return switch (this) {
        case BOOK_NOT_INITIALIZED -> ContractResponse.FailureCategory.PRECONDITION;
        case UNKNOWN_TAX_REGISTRATION, OBLIGATION_PERIOD_MISMATCH ->
            ContractResponse.FailureCategory.DOMAIN_SEMANTIC;
      };
    }

    abstract List<ContractResponse.FieldDescriptor> detailFields();
  }
}
