package dev.erst.fingrind.core;

import dev.erst.fingrind.core.JournalLine.EntrySide;
import java.util.Objects;

/** Direction-aware anchor incidence used by the journal classifier. */
public record AnchorEntry(AccountRole role, EntrySide side) {
  /** Validates one anchor incidence. */
  public AnchorEntry {
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(side, "side");
    if (!role.anchorRole()) {
      throw new IllegalArgumentException("Anchor entries require one anchor accountRole.");
    }
  }
}
