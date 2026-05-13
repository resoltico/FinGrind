package dev.erst.fingrind.contract.discovery;

import java.util.List;
import java.util.Objects;

/** Shared support for sealed descriptor namespace inventories. */
public final class DescriptorNamespaceSupport {
  private DescriptorNamespaceSupport() {}

  /** Returns the permitted descriptor record types published by one sealed descriptor root. */
  public static List<Class<?>> descriptorTypes(Class<?> descriptorRoot) {
    Class<?> sealedDescriptorRoot = Objects.requireNonNull(descriptorRoot, "descriptorRoot");
    if (!sealedDescriptorRoot.isSealed()) {
      throw new IllegalArgumentException(
          "Descriptor namespace root must be sealed: " + sealedDescriptorRoot.getName());
    }
    return List.of(sealedDescriptorRoot.getPermittedSubclasses());
  }
}
