package dev.erst.fingrind.contract.bookkeeping;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Combines independently owned administration descriptor partitions into one immutable catalog. */
final class BookAdministrationRejectionDescriptorDefinitionSupport {
  private BookAdministrationRejectionDescriptorDefinitionSupport() {}

  @SafeVarargs
  static Map<
          BookAdministrationRejectionDescriptors.Descriptor,
          BookAdministrationRejectionDescriptorDefinition>
      merge(
          Map<
                  BookAdministrationRejectionDescriptors.Descriptor,
                  BookAdministrationRejectionDescriptorDefinition>...
              definitions) {
    return Stream.of(definitions)
        .flatMap(definitionMap -> definitionMap.entrySet().stream())
        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
