package dev.erst.fingrind.contract.internal;

import dev.erst.fingrind.contract.runtime.ContractResponse;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Internal support for building static machine-readable rejection descriptor catalogs. */
public final class ContractRejectionDescriptors {
  private ContractRejectionDescriptors() {}

  /** Creates one typed detail-field descriptor for a rejection catalog entry. */
  public static ContractResponse.FieldDescriptor detailField(String name, String description) {
    return new ContractResponse.FieldDescriptor(name, description);
  }

  /** Creates one rejection descriptor with the standard empty nested-rejections shape. */
  public static ContractResponse.RejectionDescriptor descriptor(
      String code,
      ContractResponse.FailureCategory category,
      String description,
      List<ContractResponse.FieldDescriptor> detailFields) {
    return new ContractResponse.RejectionDescriptor(
        code, category, description, detailFields, List.of());
  }

  /** Projects one enum-backed static rejection catalog into public descriptor rows. */
  public static <E extends Enum<E>> List<ContractResponse.RejectionDescriptor> descriptors(
      E[] values, Function<E, ContractResponse.RejectionDescriptor> descriptorFor) {
    return Arrays.stream(values).map(descriptorFor).toList();
  }
}
