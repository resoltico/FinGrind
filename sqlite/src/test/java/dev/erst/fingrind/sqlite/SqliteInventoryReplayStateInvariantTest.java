package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused invariant coverage for the persisted inventory replay state owner. */
class SqliteInventoryReplayStateInvariantTest {
  private static final Class<?> INVENTORY_REPLAY_STATE_TYPE = inventoryReplayStateType();
  private static final LocalDate MOVEMENT_DATE = LocalDate.parse("2026-07-05");
  private static final MethodHandle INVENTORY_REPLAY_STATE_CONSTRUCTOR =
      verifierNestedHelper(
          "<init>", MethodType.methodType(void.class, long.class, long.class, Optional.class));
  private static final MethodHandle INVENTORY_REPLAY_STATE_APPLY =
      verifierNestedHelper(
          "apply",
          MethodType.methodType(
              INVENTORY_REPLAY_STATE_TYPE, long.class, long.class, LocalDate.class));
  private static final MethodHandle INVENTORY_REPLAY_STATE_VALID =
      verifierNestedHelper("valid", MethodType.methodType(boolean.class));

  @Test
  void inventoryReplayState_invariantsCoverAllQuantityAndPoolShapes() {
    assertInventoryReplayState(0L, 0L, Optional.of(MOVEMENT_DATE), true);
    assertInventoryReplayState(5L, 10L, Optional.of(MOVEMENT_DATE), true);
    assertInventoryReplayState(0L, 5L, Optional.of(MOVEMENT_DATE), false);
    assertInventoryReplayState(5L, 0L, Optional.of(MOVEMENT_DATE), false);
    assertInventoryReplayState(-1L, 5L, Optional.of(MOVEMENT_DATE), false);
    assertInventoryReplayState(5L, -1L, Optional.of(MOVEMENT_DATE), false);
    assertInventoryReplayState(0L, 0L, Optional.empty(), false);

    Object replaySentinel = newInventoryReplayState(0L, 0L, Optional.empty());
    assertTrue(inventoryReplayStateValid(applyInventoryReplayState(replaySentinel)));
  }

  private static void assertInventoryReplayState(
      long quantity,
      long costPoolMinor,
      Optional<LocalDate> lastMovementDate,
      boolean expectedValid) {
    Object state = newInventoryReplayState(quantity, costPoolMinor, lastMovementDate);
    assertEquals(expectedValid, inventoryReplayStateValid(state));
  }

  private static Object newInventoryReplayState(
      long quantity, long costPoolMinor, Optional<LocalDate> lastMovementDate) {
    try {
      return INVENTORY_REPLAY_STATE_CONSTRUCTOR.invoke(quantity, costPoolMinor, lastMovementDate);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to construct SQLite inventory replay state.", throwable);
    }
  }

  private static boolean inventoryReplayStateValid(Object state) {
    try {
      return (boolean) INVENTORY_REPLAY_STATE_VALID.invoke(state);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke SQLite inventory replay state valid().", throwable);
    }
  }

  private static Object applyInventoryReplayState(Object state) {
    try {
      return INVENTORY_REPLAY_STATE_APPLY.invoke(state, 5L, 10L, MOVEMENT_DATE);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError(
          "Failed to invoke SQLite inventory replay state apply(...).", throwable);
    }
  }

  private static Class<?> inventoryReplayStateType() {
    try {
      return Class.forName(SqliteBookIntegrityVerifier.class.getName() + "$InventoryReplayState");
    } catch (ClassNotFoundException exception) {
      throw new LinkageError("Failed to load SQLite inventory replay state helper.", exception);
    }
  }

  private static MethodHandle verifierNestedHelper(String methodName, MethodType methodType) {
    try {
      MethodHandles.Lookup lookup =
          MethodHandles.privateLookupIn(INVENTORY_REPLAY_STATE_TYPE, MethodHandles.lookup());
      if ("<init>".equals(methodName)) {
        return lookup.findConstructor(INVENTORY_REPLAY_STATE_TYPE, methodType);
      }
      return lookup.findVirtual(INVENTORY_REPLAY_STATE_TYPE, methodName, methodType);
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError(
          "Failed to bind SQLite inventory replay state helper: " + methodName, exception);
    }
  }
}
