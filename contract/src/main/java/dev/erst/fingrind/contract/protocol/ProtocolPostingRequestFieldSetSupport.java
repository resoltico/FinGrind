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
                ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
                ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
                ProtocolPostEntryFields.TopLevel.EVIDENCE,
                ProtocolPostEntryFields.TopLevel.PROVENANCE));
    java.util.Collections.addAll(fields, variantFields);
    return Set.copyOf(fields);
  }
}
