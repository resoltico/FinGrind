package dev.erst.fingrind.contract.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import org.junit.jupiter.api.Test;

/** Protects machine-discovery prose from retaining a partial enumeration of entry kinds. */
class MachineContractPostEntryFieldSpecsTest {
  @Test
  void entryKindDescription_pointsToTheLiveVariantContractInsteadOfAStalePartialList() {
    String description =
        MachineContractPostEntryFieldSpecs.topLevelFields().stream()
            .filter(field -> ProtocolPostEntryFields.TopLevel.ENTRY_KIND.equals(field.name()))
            .findFirst()
            .orElseThrow()
            .description();

    assertEquals(
        "Caller-authored bookkeeping entry kind. FinGrind accepts direct journals and published "
            + "command-specific typed variants; use machine discovery or command help for each "
            + "variant's exact requirements.",
        description);
    assertFalse(description.contains("sales, purchases"));
  }
}
