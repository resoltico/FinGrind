package dev.erst.fingrind.contract.internal;

import dev.erst.fingrind.contract.runtime.FailureCategory;
import dev.erst.fingrind.contract.runtime.FieldDescriptor;
import dev.erst.fingrind.contract.runtime.RejectionDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Internal support for building static machine-readable rejection descriptor catalogs. */
public final class ContractRejectionDescriptors {
  private ContractRejectionDescriptors() {}

  /** Creates one typed detail-field descriptor for a rejection catalog entry. */
  public static FieldDescriptor detailField(String name, String description) {
    return new FieldDescriptor(name, description);
  }

  /** Creates one rejection descriptor with the standard empty nested-rejections shape. */
  public static RejectionDescriptor descriptor(
      String code,
      FailureCategory category,
      String description,
      List<FieldDescriptor> detailFields) {
    return new RejectionDescriptor(code, category, description, detailFields, List.of());
  }

  /** Projects one enum-backed static rejection catalog into public descriptor rows. */
  public static <E extends Enum<E>> List<RejectionDescriptor> descriptors(
      E[] values, Function<E, RejectionDescriptor> descriptorFor) {
    return Arrays.stream(values).map(descriptorFor).toList();
  }
}
