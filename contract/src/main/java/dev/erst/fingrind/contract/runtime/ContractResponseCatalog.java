package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.tax.TaxDeclarationRejection;
import dev.erst.fingrind.contract.tax.TaxQueryRejection;
import dev.erst.fingrind.contract.workflow.LedgerPlanFailure;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/** Owns the complete published catalog of response failures and their transport categories. */
public final class ContractResponseCatalog {
  private static final List<ContractResponse.ErrorDescriptor> ERROR_DESCRIPTORS =
      Stream.concat(
              ContractErrors.descriptors().stream(), LedgerPlanFailure.errorDescriptors().stream())
          .toList();
  private static final List<ContractResponse.RejectionDescriptor> REJECTION_DESCRIPTORS =
      Stream.of(
              BookAdministrationRejection.descriptors(),
              BookMaintenanceRejection.descriptors(),
              BookQueryRejection.descriptors(),
              TaxDeclarationRejection.descriptors(),
              TaxQueryRejection.descriptors(),
              PostingRejection.descriptors(),
              LedgerPlanFailure.rejectionDescriptors())
          .flatMap(List::stream)
          .toList();
  private static final Map<String, ContractResponse.FailureCategory> CATEGORIES_BY_CODE =
      categoriesByCode();

  private ContractResponseCatalog() {}

  /** Returns descriptors for every public deterministic error. */
  public static List<ContractResponse.ErrorDescriptor> errorDescriptors() {
    return ERROR_DESCRIPTORS;
  }

  /** Returns the descriptor for one published deterministic error. */
  public static ContractResponse.ErrorDescriptor errorDescriptorFor(String code) {
    String failureCode = Objects.requireNonNull(code, "code");
    return ERROR_DESCRIPTORS.stream()
        .filter(descriptor -> descriptor.code().equals(failureCode))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No published error descriptor exists for code: " + failureCode));
  }

  /** Returns descriptors for every public deterministic rejection. */
  public static List<ContractResponse.RejectionDescriptor> rejectionDescriptors() {
    return REJECTION_DESCRIPTORS;
  }

  /** Returns the explicitly declared transport category for one public failure code. */
  public static ContractResponse.FailureCategory failureCategoryFor(String code) {
    String failureCode = Objects.requireNonNull(code, "code");
    ContractResponse.FailureCategory category = CATEGORIES_BY_CODE.get(failureCode);
    if (category == null) {
      throw new IllegalArgumentException(
          "No published failure category exists for code: " + failureCode);
    }
    return category;
  }

  static Map<String, ContractResponse.FailureCategory> categoriesByCode() {
    Map<String, ContractResponse.FailureCategory> categories = new ConcurrentHashMap<>();
    ERROR_DESCRIPTORS.forEach(
        descriptor -> register(categories, descriptor.code(), descriptor.category()));
    REJECTION_DESCRIPTORS.forEach(descriptor -> registerRecursively(categories, descriptor));
    return Map.copyOf(categories);
  }

  private static void registerRecursively(
      Map<String, ContractResponse.FailureCategory> categories,
      ContractResponse.RejectionDescriptor descriptor) {
    register(categories, descriptor.code(), descriptor.category());
    descriptor.detailRejections().forEach(detail -> registerRecursively(categories, detail));
  }

  static void register(
      Map<String, ContractResponse.FailureCategory> categories,
      String code,
      ContractResponse.FailureCategory category) {
    ContractResponse.FailureCategory prior = categories.putIfAbsent(code, category);
    if (prior != null && prior != category) {
      throw new IllegalStateException("Conflicting published failure categories for code: " + code);
    }
  }
}
