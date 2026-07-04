package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.JournalLine.EntrySide;
import org.junit.jupiter.api.Test;

/** Covers anchor-entry construction and its anchor-role guard. */
class AnchorEntryTest {
  @Test
  void constructor_acceptsAnchorRoles() {
    AnchorEntry anchorEntry = new AnchorEntry(AccountRole.CASH, EntrySide.DEBIT);

    assertEquals(AccountRole.CASH, anchorEntry.role());
    assertEquals(EntrySide.DEBIT, anchorEntry.side());
  }

  @Test
  void constructor_rejectsNonAnchorRoles() {
    IllegalArgumentException rejection =
        assertThrows(
            IllegalArgumentException.class,
            () -> new AnchorEntry(AccountRole.SETTLEMENT_ADJUNCT, EntrySide.CREDIT));

    assertEquals("Anchor entries require one anchor accountRole.", rejection.getMessage());
  }

  @Test
  void constructor_requiresNonNullValues() {
    assertThrows(NullPointerException.class, () -> new AnchorEntry(nullOf(), EntrySide.DEBIT));
    assertThrows(NullPointerException.class, () -> new AnchorEntry(AccountRole.CASH, nullOf()));
  }
}
