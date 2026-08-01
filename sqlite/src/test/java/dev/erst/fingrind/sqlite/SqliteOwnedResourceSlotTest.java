package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Direct state-transition coverage for one transfer-only resource slot. */
class SqliteOwnedResourceSlotTest {
  @Test
  void emptySlotCanTransferAnOptionalAbsenceButCannotBeReused() {
    SqliteOwnedResourceSlot<String> slot =
        SqliteOwnedResourceSlot.create("resource", ignored -> {});

    assertNull(slot.peekNullable());
    assertEquals(
        "resource is not owned.",
        assertThrows(IllegalStateException.class, slot::peekRequired).getMessage());
    slot.transferToSuccessor();

    assertEquals(
        "resource ownership has already transferred or been released.",
        assertThrows(IllegalStateException.class, slot::transferToSuccessor).getMessage());
    assertDoesNotThrow(slot::releaseIfHeld);
    assertEquals(
        "resource ownership has already transferred or been released.",
        assertThrows(IllegalStateException.class, () -> slot.hold("later")).getMessage());
    assertEquals(
        "resource ownership has already transferred or been released.",
        assertThrows(
                IllegalStateException.class,
                () -> {
                  slot.peekNullable();
                })
            .getMessage());
  }

  @Test
  void heldSlotTransfersItsExactResourceAndRejectsDuplicateCapture() {
    String resource = "held";
    SqliteOwnedResourceSlot<String> slot =
        SqliteOwnedResourceSlot.create("resource", ignored -> {});
    slot.hold(resource);

    assertSame(resource, slot.peekNullable());
    assertSame(resource, slot.peekRequired());
    assertEquals(
        "resource is already owned.",
        assertThrows(IllegalStateException.class, () -> slot.hold("duplicate")).getMessage());

    slot.transferToSuccessor();

    assertDoesNotThrow(slot::releaseIfHeld);
  }

  @Test
  void releaseClearsOwnershipBeforeAnActionFailureAndNeverRetriesIt() {
    AtomicInteger releaseCount = new AtomicInteger();
    IllegalStateException releaseFailure = new IllegalStateException("release failure");
    SqliteOwnedResourceSlot<String> slot =
        SqliteOwnedResourceSlot.create(
            "resource",
            ignored -> {
              releaseCount.incrementAndGet();
              throw releaseFailure;
            });
    slot.hold("held");

    assertSame(releaseFailure, assertThrows(IllegalStateException.class, slot::releaseIfHeld));
    assertEquals(1, releaseCount.get());
    assertDoesNotThrow(slot::releaseIfHeld);
    assertEquals(1, releaseCount.get());
    assertEquals(
        "resource ownership has already transferred or been released.",
        assertThrows(IllegalStateException.class, () -> slot.hold("later")).getMessage());
  }
}
