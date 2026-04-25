package dev.erst.fingrind.contract;

import java.util.List;
import java.util.Objects;

/** Shared support for sealed descriptor namespace inventories. */
final class DescriptorNamespaceSupport {
  private DescriptorNamespaceSupport() {}

  static List<Class<?>> descriptorTypes(Class<?> descriptorRoot) {
    Class<?> sealedDescriptorRoot = Objects.requireNonNull(descriptorRoot, "descriptorRoot");
    if (!sealedDescriptorRoot.isSealed()) {
      throw new IllegalArgumentException(
          "Descriptor namespace root must be sealed: " + sealedDescriptorRoot.getName());
    }
    return List.of(sealedDescriptorRoot.getPermittedSubclasses());
  }
}
