package dev.erst.fingrind.contract;

import java.util.List;

/** Discovery descriptor namespace for the public machine-readable CLI contract. */
public final class ContractDiscovery {
  private ContractDiscovery() {}

  /** Returns the descriptor record types owned by this namespace. */
  public static List<Class<?>> descriptorTypes() {
    return DescriptorNamespaceSupport.descriptorTypes(ContractDiscoveryDescriptor.class);
  }
}
