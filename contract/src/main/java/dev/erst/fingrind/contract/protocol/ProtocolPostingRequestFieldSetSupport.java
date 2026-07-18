package dev.erst.fingrind.contract.protocol;

import java.util.LinkedHashSet;
import java.util.Set;

/** Builds canonical typed-posting field sets from the invariant request envelope fields. */
final class ProtocolPostingRequestFieldSetSupport {
  private ProtocolPostingRequestFieldSetSupport() {}

  static Set<String> typedEntryFields(String... variantFields) {
    var fields =
        new LinkedHashSet<>(
            Set.of(
                ProtocolBusinessEventFields.Core.ENTRY_KIND,
                ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
                ProtocolBusinessEventFields.Core.EVIDENCE,
                ProtocolBusinessEventFields.Core.PROVENANCE));
    java.util.Collections.addAll(fields, variantFields);
    return Set.copyOf(fields);
  }
}
