package dev.erst.fingrind.sqlite;

import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * Holds one transfer-only resource until a later owner claims or releases it.
 *
 * <p>Every successful {@link #hold(Object)} has exactly one terminal transition: a caller takes the
 * value into a new owner, or {@link #releaseIfHeld()} invokes the declared release action. Clearing
 * the slot before either transition makes duplicate release impossible even when the release action
 * throws.
 */
final class SqliteOwnedResourceSlot<T> {
  /** Terminal and in-progress ownership states that prevent duplicate resource release. */
  private enum OwnershipState {
    EMPTY,
    HELD,
    TRANSFERRED,
    RELEASED
  }

  private final String ownershipName;
  private final Consumer<T> releaseAction;
  private @Nullable T owned;
  private OwnershipState state = OwnershipState.EMPTY;

  private SqliteOwnedResourceSlot(String ownershipName, Consumer<T> releaseAction) {
    this.ownershipName = Objects.requireNonNull(ownershipName, "ownershipName");
    this.releaseAction = Objects.requireNonNull(releaseAction, "releaseAction");
  }

  /** Creates one empty resource slot with its exact release action. */
  static <T> SqliteOwnedResourceSlot<T> create(String ownershipName, Consumer<T> releaseAction) {
    return new SqliteOwnedResourceSlot<>(ownershipName, releaseAction);
  }

  /** Transfers a non-null resource into this still-empty slot. */
  void hold(T resource) {
    if (state == OwnershipState.HELD) {
      throw new IllegalStateException(ownershipName + " is already owned.");
    }
    if (state != OwnershipState.EMPTY) {
      throw new IllegalStateException(
          ownershipName + " ownership has already transferred or been released.");
    }
    owned = Objects.requireNonNull(resource, ownershipName);
    state = OwnershipState.HELD;
  }

  /**
   * Returns the held resource for successor construction without transferring closure authority.
   */
  T peekRequired() {
    if (state == OwnershipState.EMPTY) {
      throw new IllegalStateException(ownershipName + " is not owned.");
    }
    return Objects.requireNonNull(peekNullable(), ownershipName);
  }

  /**
   * Returns the held resource for optional successor construction without transferring authority.
   */
  @Nullable T peekNullable() {
    if (state == OwnershipState.EMPTY) {
      return null;
    }
    if (state != OwnershipState.HELD) {
      throw new IllegalStateException(
          ownershipName + " ownership has already transferred or been released.");
    }
    return owned;
  }

  /**
   * Completes a successor transfer after the successor has been constructed from a peeked value.
   */
  void transferToSuccessor() {
    if (state == OwnershipState.EMPTY) {
      state = OwnershipState.TRANSFERRED;
      return;
    }
    if (state != OwnershipState.HELD) {
      throw new IllegalStateException(
          ownershipName + " ownership has already transferred or been released.");
    }
    owned = null;
    state = OwnershipState.TRANSFERRED;
  }

  /** Releases the held resource when it has not already transferred to a successor owner. */
  void releaseIfHeld() {
    if (state == OwnershipState.TRANSFERRED || state == OwnershipState.RELEASED) {
      return;
    }
    @Nullable T releasing = owned;
    if (releasing != null) {
      owned = null;
      state = OwnershipState.RELEASED;
      releaseAction.accept(releasing);
      return;
    }
    state = OwnershipState.RELEASED;
  }
}
