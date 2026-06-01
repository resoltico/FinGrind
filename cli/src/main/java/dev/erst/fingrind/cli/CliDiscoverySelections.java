package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.protocol.DiscoveryFocus;
import dev.erst.fingrind.contract.protocol.OperationCategory;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Parsed optional discovery-slice selectors for JSON help and capabilities commands. */
record CliDiscoverySelections(DiscoveryFocus focus, @Nullable OperationCategory category) {
  CliDiscoverySelections {
    Objects.requireNonNull(focus, "focus");
  }

  static CliDiscoverySelections overview() {
    return new CliDiscoverySelections(DiscoveryFocus.OVERVIEW, null);
  }
}
